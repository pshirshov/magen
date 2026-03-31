package io.septimalmind.magen.gui

import io.septimalmind.magen.Magen
import io.septimalmind.magen.cli.ParsedArgs
import io.septimalmind.magen.model.{Mapping, Platform, SchemeId}
import io.septimalmind.magen.targets.{IdeaInstaller, IdeaParams, IdeaRenderer, VSCodeRenderer, VscodeInstaller, VscodeParams, ZedInstaller, ZedParams, ZedRenderer}
import io.septimalmind.magen.util.{DefaultPaths, PathExpander}

import java.awt.{BorderLayout, Button, Choice, EventQueue, FileDialog, FlowLayout, Frame, Label, Panel, TextArea}
import java.awt.event.{ActionEvent, ActionListener, WindowAdapter, WindowEvent}
import java.io.{OutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object MagenGui {
  def launch(parsed: ParsedArgs): Unit = {
    // Native-image doesn't set java.home, which AWT font config requires
    val javaHome = System.getProperty("java.home")
    if (javaHome == null || javaHome.isEmpty) {
      val envHome = System.getenv("JAVA_HOME")
      if (envHome != null) {
        System.setProperty("java.home", envHome)
      }
    }

    System.err.println(s"MagenGui: dark=${GuiTheme.isDark}, scale=${GuiTheme.uiScale}")

    val frame = new MagenFrame(parsed)
    frame.setVisible(true)
  }
}

class MagenFrame(parsed: ParsedArgs) extends Frame("Magen - Keyboard Mapping Generator") {
  private val hostPlatform: Platform = Platform.detect()
  private val theme                  = GuiTheme

  // -- UI Components --
  private val schemeChoice   = new Choice()
  private val platformChoice = new Choice()
  private val generateBtn    = new Button("Generate")
  private val renderBtn      = new Button("Render...")
  private val scanBtn        = new Button("Scan")
  private val loadBtn        = new Button("Load")
  private val bindingTable   = new BindingTable()
  private val detailPanel    = new BindingDetailPanel()
  private val logArea        = new TextArea("", 6, 80, TextArea.SCROLLBARS_VERTICAL_ONLY)
  private val statusLabel    = new Label("Ready")

  @scala.annotation.nowarn("msg=never used")
  private var currentMapping: Option[Mapping] = None

  init()

  private def init(): Unit = {
    val gap = theme.scaled(4)
    setLayout(new BorderLayout(gap, gap))
    setBackground(theme.background)
    setForeground(theme.foreground)

    // -- Top panel: controls --
    val controlPanel = new Panel(new FlowLayout(FlowLayout.LEFT, theme.scaled(8), theme.scaled(4)))
    controlPanel.setBackground(theme.background)

    val schemeLabel = new Label("Scheme:")
    schemeLabel.setFont(theme.sansFont(13))
    schemeLabel.setForeground(theme.foreground)
    controlPanel.add(schemeLabel)

    populateSchemes()
    parsed.scheme.foreach(s => selectChoice(schemeChoice, s.name))
    schemeChoice.setFont(theme.sansFont(13))
    controlPanel.add(schemeChoice)

    val platformLabel = new Label("Platform:")
    platformLabel.setFont(theme.sansFont(13))
    platformLabel.setForeground(theme.foreground)
    controlPanel.add(platformLabel)

    platformChoice.add("linux")
    platformChoice.add("macos")
    platformChoice.add("win")
    selectChoice(platformChoice, parsed.platform.getOrElse(hostPlatform).toString.toLowerCase)
    platformChoice.setFont(theme.sansFont(13))
    controlPanel.add(platformChoice)

    val buttons = scala.collection.immutable.List(loadBtn, generateBtn, renderBtn, scanBtn)
    buttons.foreach {
      btn =>
        btn.setFont(theme.sansBold(13))
        btn.setBackground(theme.controlBg)
        btn.setForeground(theme.foreground)
        controlPanel.add(btn)
    }

    add(controlPanel, BorderLayout.NORTH)

    // -- Center: binding table --
    bindingTable.setSelectionListener(c => detailPanel.showConcept(c))
    add(bindingTable, BorderLayout.CENTER)

    // -- Bottom: detail panel + log + status --
    val bottomPanel = new Panel(new BorderLayout(gap, theme.scaled(2)))
    bottomPanel.setBackground(theme.background)

    bottomPanel.add(detailPanel, BorderLayout.NORTH)

    val logPanel = new Panel(new BorderLayout(gap, 0))
    logPanel.setBackground(theme.background)
    val logLabel = new Label("Log:")
    logLabel.setFont(theme.sansBold(12))
    logLabel.setForeground(theme.foreground)
    logPanel.add(logLabel, BorderLayout.NORTH)

    logArea.setEditable(false)
    logArea.setFont(theme.monoFont(11))
    logArea.setBackground(theme.surfaceAlt)
    logArea.setForeground(theme.foreground)
    logPanel.add(logArea, BorderLayout.CENTER)

    bottomPanel.add(logPanel, BorderLayout.CENTER)

    statusLabel.setFont(theme.sansFont(12))
    statusLabel.setBackground(theme.statusBg)
    statusLabel.setForeground(theme.foregroundDim)
    bottomPanel.add(statusLabel, BorderLayout.SOUTH)

    add(bottomPanel, BorderLayout.SOUTH)

    // -- Event handlers --
    loadBtn.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = loadScheme()
    })

    generateBtn.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = doGenerate()
    })

    renderBtn.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = doRender()
    })

    scanBtn.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = doScan()
    })

    addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit = {
        dispose()
        sys.exit(0)
      }
    })

    // -- Size and position --
    setSize(theme.scaled(1100), theme.scaled(750))
    setLocationRelativeTo(null)
  }

  private def populateSchemes(): Unit = {
    schemeChoice.removeAll()
    val schemes = Magen.listSchemes()
    schemes.foreach(s => schemeChoice.add(s.name))
    if (schemes.isEmpty) {
      schemeChoice.add("(none)")
    }
  }

  private def selectChoice(choice: Choice, value: String): Unit = {
    for (i <- 0 until choice.getItemCount) {
      if (choice.getItem(i).equalsIgnoreCase(value)) {
        choice.select(i)
        return
      }
    }
  }

  private def selectedScheme: SchemeId = SchemeId(schemeChoice.getSelectedItem)

  private def selectedPlatform: Platform = Platform.parse(platformChoice.getSelectedItem)

  private def log(msg: String): Unit = {
    logArea.append(msg + "\n")
    logArea.setCaretPosition(logArea.getText.length)
  }

  private def setStatus(msg: String): Unit = {
    statusLabel.setText(msg)
  }

  private def withCapturedOutput(task: => Unit): Unit = {
    val origOut = System.out
    val origErr = System.err
    val capturer = new OutputStream {
      private val sb = new StringBuilder
      override def write(b: Int): Unit = {
        if (b == '\n') {
          val line = sb.toString()
          sb.clear()
          EventQueue.invokeLater(() => log(line))
        } else {
          sb.append(b.toChar)
        }
      }
      override def flush(): Unit = {
        if (sb.nonEmpty) {
          val line = sb.toString()
          sb.clear()
          EventQueue.invokeLater(() => log(line))
        }
      }
    }
    val ps = new PrintStream(capturer, true, StandardCharsets.UTF_8)
    try {
      System.setOut(ps)
      System.setErr(ps)
      task
    } finally {
      ps.flush()
      System.setOut(origOut)
      System.setErr(origErr)
    }
  }

  private def loadScheme(): Unit = {
    val scheme   = selectedScheme
    val platform = selectedPlatform

    setStatus(s"Loading ${scheme.name} ($platform)...")
    logArea.setText("")
    detailPanel.clear()

    runAsync {
      withCapturedOutput {
        val (mapping, validation) = Magen.loadAndValidate(scheme, platform)
        currentMapping = Some(mapping)

        EventQueue.invokeLater {
          () =>
            bindingTable.setData(mapping)

            if (validation.errors.nonEmpty || validation.ideaConflicts.nonEmpty || validation.missingBindings.nonEmpty) {
              validation.missingBindings.foreach(m => log(s"MISSING: ${m.message}"))
              validation.ideaConflicts.foreach(c => log(s"IDEA CONFLICT (warn): ${c.message}"))
              validation.errors.foreach(c => log(s"ERROR: ${c.message}"))
            }

            setStatus(s"Loaded ${scheme.name}: ${mapping.mapping.size} bindings")
        }
      }
    }
  }

  private def doGenerate(): Unit = {
    val scheme   = selectedScheme
    val platform = selectedPlatform

    setStatus(s"Generating ${scheme.name} ($platform)...")

    runAsync {
      withCapturedOutput {
        val (mapping, validation) = Magen.loadAndValidate(scheme, platform)

        if (validation.errors.nonEmpty) {
          EventQueue.invokeLater {
            () =>
              setStatus(s"Generation failed: ${validation.errors.size} binding conflict(s)")
          }
          return
        }

        val keymapName = s"Magen-${scheme.name}"
        val config     = io.septimalmind.magen.config.MagenConfig.empty

        val installers = scala.collection.immutable.List(
          new VscodeInstaller(
            VscodeParams(DefaultPaths.vscodePaths(hostPlatform) ++ config.`installer-paths`.vscode),
            platform,
          ),
          new IdeaInstaller(
            IdeaParams(
              DefaultPaths.ideaPaths(hostPlatform, keymapName) ++ config.`installer-paths`.idea,
              negate     = true,
              parent     = "$default",
              keymapName = keymapName,
            ),
            platform,
          ),
          new ZedInstaller(
            ZedParams(DefaultPaths.zedPaths(hostPlatform) ++ config.`installer-paths`.zed),
            platform,
          ),
        )

        installers.foreach(_.install(mapping))

        EventQueue.invokeLater {
          () =>
            setStatus(s"Generated and installed ${scheme.name}")
        }
      }
    }
  }

  private def doRender(): Unit = {
    val fd = new FileDialog(this, "Select output directory", FileDialog.SAVE)
    fd.setFile("output")
    fd.setVisible(true)

    val dir  = fd.getDirectory
    val file = fd.getFile
    if (dir == null || file == null) return

    val outputDir = Paths.get(dir, file)
    Files.createDirectories(outputDir)

    val scheme   = selectedScheme
    val platform = selectedPlatform

    setStatus(s"Rendering ${scheme.name} to $outputDir...")

    runAsync {
      withCapturedOutput {
        val (mapping, _) = Magen.loadAndValidate(scheme, platform)
        val keymapName   = s"Magen-${scheme.name}"

        val vscodeOut = VSCodeRenderer.render(mapping, platform)
        Files.write(outputDir.resolve("keybindings.json"), vscodeOut.getBytes(StandardCharsets.UTF_8))
        println(s"Wrote ${outputDir.resolve("keybindings.json")}")

        val ideaRenderer = new IdeaRenderer(IdeaParams(Nil, negate = true, parent = "$default", keymapName = keymapName))
        val ideaOut      = ideaRenderer.render(mapping, platform)
        Files.write(outputDir.resolve(s"$keymapName.xml"), ideaOut.getBytes(StandardCharsets.UTF_8))
        println(s"Wrote ${outputDir.resolve(s"$keymapName.xml")}")

        val zedOut = ZedRenderer.render(mapping, platform)
        Files.write(outputDir.resolve("keymap.json"), zedOut.getBytes(StandardCharsets.UTF_8))
        println(s"Wrote ${outputDir.resolve("keymap.json")}")

        EventQueue.invokeLater {
          () =>
            setStatus(s"Rendered to $outputDir")
        }
      }
    }
  }

  private def doScan(): Unit = {
    setStatus("Scanning for editors...")
    logArea.setText("")

    runAsync {
      val lines = new StringBuilder

      lines.append(s"Platform: $hostPlatform\n\n")

      lines.append("=== VSCode / VSCodium ===\n")
      val vscodePaths = PathExpander
        .expandGlobs(DefaultPaths.vscodePaths(hostPlatform))
        .filter(p => Files.exists(p) && Files.isRegularFile(p))
      if (vscodePaths.isEmpty) lines.append("  (none found)\n")
      else vscodePaths.foreach(p => lines.append(s"  $p\n"))

      lines.append("\n=== Zed ===\n")
      val zedPaths = PathExpander
        .expandGlobs(DefaultPaths.zedPaths(hostPlatform))
        .filter(p => Files.exists(p) && Files.isRegularFile(p))
      if (zedPaths.isEmpty) lines.append("  (none found)\n")
      else zedPaths.foreach(p => lines.append(s"  $p\n"))

      lines.append("\n=== IntelliJ IDEA ===\n")
      val keymaps = io.septimalmind.magen.importer.IdeaSchemeImporter.listKeymaps()
      if (keymaps.isEmpty) {
        lines.append("  (none found)\n")
      } else {
        val (user, bundled) = keymaps.partition(!_.bundled)
        if (user.nonEmpty) {
          lines.append("  User keymaps:\n")
          user.foreach(km => lines.append(s"    ${km.name} (parent: ${km.parent}, source: ${km.source})\n"))
        }
        if (bundled.nonEmpty) {
          lines.append("  Bundled keymaps:\n")
          bundled.foreach(km => lines.append(s"    ${km.name} (parent: ${km.parent}, source: ${km.source})\n"))
        }
      }

      EventQueue.invokeLater {
        () =>
          logArea.setText(lines.toString())
          logArea.setCaretPosition(0)
          setStatus("Scan complete")
      }
    }
  }

  private def runAsync(task: => Unit): Unit = {
    val thread = new Thread(
      () => {
        try {
          task
        } catch {
          case e: Exception =>
            EventQueue.invokeLater {
              () =>
                log(s"ERROR: ${e.getMessage}")
                setStatus(s"Error: ${e.getMessage}")
            }
        }
      }
    )
    thread.setDaemon(true)
    thread.start()
  }
}
