package io.septimalmind.magen.gui

import io.septimalmind.magen.model.{Concept, Mapping}

import java.awt.{BorderLayout, Canvas, Checkbox, Color, Dimension, Font, FontMetrics, Graphics, Graphics2D, Image, Panel, RenderingHints, Scrollbar, TextField, Label}
import java.awt.event.{ItemEvent, KeyAdapter, KeyEvent, MouseAdapter, MouseEvent, MouseWheelEvent}

/** Column definition for the binding table. */
case class TableColumn(name: String, width: Int, extract: Concept => String)

/** A custom AWT table component with column headers, row selection, scrolling, and search filtering.
  * Supports text search and hotkey search (press keys to find bindings by chord).
  */
class BindingTable extends Panel {
  private val theme = GuiTheme

  private var allRows: List[Concept]                     = Nil
  private var filteredRows: List[Concept]                 = Nil
  private var selectedIndex: Int                          = -1
  private var scrollOffset: Int                           = 0
  private var searchText: String                          = ""
  private var hotkeyMode: Boolean                         = false
  private var onSelectionChange: Option[Concept => Unit]  = None

  private val rowHeight: Int    = theme.scaled(22)
  private val headerHeight: Int = theme.scaled(26)

  private val columns: List[TableColumn] = List(
    TableColumn("Concept", theme.scaled(220), _.id),
    TableColumn(
      "Binding",
      theme.scaled(200),
      c => c.binding.toList.map(chord => chord.combos.map(formatCombo).mkString(" ")).mkString(" / "),
    ),
    TableColumn("IDEA", theme.scaled(200), _.idea.map(_.action).getOrElse("-")),
    TableColumn("VSCode", theme.scaled(220), _.vscode.map(_.action).getOrElse("-")),
    TableColumn("Zed", theme.scaled(180), _.zed.map(_.action).getOrElse("-")),
  )

  private val canvas      = new TableCanvas()
  private val scrollbar   = new Scrollbar(Scrollbar.VERTICAL)
  private val searchField = new TextField()
  private val hotkeyCheck = new Checkbox("Hotkey", false)

  // Chord capture state
  private var chordParts: List[String]  = Nil
  private var lastKeyTime: Long         = 0
  private val CHORD_TIMEOUT_MS: Long    = 800

  init()

  private def init(): Unit = {
    setLayout(new BorderLayout(0, theme.scaled(2)))
    setBackground(theme.background)

    // Search bar
    val searchPanel = new Panel(new BorderLayout(theme.scaled(4), 0))
    searchPanel.setBackground(theme.background)
    val searchLabel = new Label("Search:")
    searchLabel.setFont(theme.sansFont(13))
    searchLabel.setForeground(theme.foreground)
    searchPanel.add(searchLabel, BorderLayout.WEST)

    searchField.setFont(theme.monoFont(13))
    searchField.setBackground(theme.surface)
    searchField.setForeground(theme.foreground)
    searchField.addTextListener(_ => {
      if (!hotkeyMode) {
        searchText = searchField.getText.toLowerCase.trim
        applyFilter()
      }
    })
    searchPanel.add(searchField, BorderLayout.CENTER)

    hotkeyCheck.setFont(theme.sansFont(12))
    hotkeyCheck.setForeground(theme.foreground)
    hotkeyCheck.setBackground(theme.background)
    hotkeyCheck.addItemListener((_: ItemEvent) => {
      hotkeyMode = hotkeyCheck.getState
      if (hotkeyMode) {
        searchField.setText("")
        searchField.setEditable(false)
        chordParts = Nil
        searchText = ""
        applyFilter()
      } else {
        searchField.setEditable(true)
        searchField.setText("")
        chordParts = Nil
        searchText = ""
        applyFilter()
      }
      searchField.requestFocus()
    })
    searchPanel.add(hotkeyCheck, BorderLayout.EAST)

    add(searchPanel, BorderLayout.NORTH)

    // Hotkey capture on the search field
    searchField.addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit = {
        if (!hotkeyMode) return
        e.consume()

        val keyCode = e.getKeyCode
        // Ignore pure modifier presses
        if (isModifierKey(keyCode)) return

        val now = System.currentTimeMillis()
        val combo = buildComboString(e)

        if (now - lastKeyTime > CHORD_TIMEOUT_MS) {
          // Start a new chord
          chordParts = List(combo)
        } else {
          // Continue chord
          chordParts = chordParts :+ combo
        }
        lastKeyTime = now

        val display = chordParts.mkString(" ")
        searchField.setText(display)
        searchText = display.toLowerCase
        applyFilter()
      }

      override def keyTyped(e: KeyEvent): Unit = {
        if (hotkeyMode) e.consume()
      }

      override def keyReleased(e: KeyEvent): Unit = {
        if (hotkeyMode) e.consume()
      }
    })

    // Table canvas + scrollbar
    val tablePanel = new Panel(new BorderLayout())
    tablePanel.add(canvas, BorderLayout.CENTER)
    scrollbar.setBackground(theme.controlBg)
    scrollbar.addAdjustmentListener(_ => {
      scrollOffset = scrollbar.getValue
      canvas.repaint()
    })
    tablePanel.add(scrollbar, BorderLayout.EAST)
    add(tablePanel, BorderLayout.CENTER)

    canvas.addMouseListener(new MouseAdapter {
      override def mousePressed(e: MouseEvent): Unit = {
        val clickedRow = (e.getY - headerHeight) / rowHeight + scrollOffset
        if (clickedRow >= 0 && clickedRow < filteredRows.size) {
          selectedIndex = clickedRow
          canvas.repaint()
          onSelectionChange.foreach(_(filteredRows(selectedIndex)))
        }
      }
    })

    // Enable mouse wheel events on the canvas and the whole panel
    val wheelHandler = new java.awt.event.MouseWheelListener {
      override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
        val maxScroll = Math.max(0, scrollbar.getMaximum - scrollbar.getVisibleAmount)
        val newVal    = scrollOffset + e.getWheelRotation * 3
        scrollOffset = Math.max(0, Math.min(newVal, maxScroll))
        scrollbar.setValue(scrollOffset)
        canvas.repaint()
      }
    }
    canvas.addMouseWheelListener(wheelHandler)
    addMouseWheelListener(wheelHandler)
  }

  def setData(mapping: Mapping): Unit = {
    allRows = mapping.mapping
    applyFilter()
  }

  def setSelectionListener(listener: Concept => Unit): Unit = {
    onSelectionChange = Some(listener)
  }

  def selectedConcept: Option[Concept] = {
    if (selectedIndex >= 0 && selectedIndex < filteredRows.size) Some(filteredRows(selectedIndex))
    else None
  }

  private def applyFilter(): Unit = {
    filteredRows = if (searchText.isEmpty) {
      allRows
    } else {
      allRows.filter { c =>
        val bindingStr = c.binding.toList
          .map(chord => chord.combos.map(formatCombo).mkString(" "))
          .mkString(" / ")
          .toLowerCase

        c.id.toLowerCase.contains(searchText) ||
        bindingStr.contains(searchText) ||
        c.idea.exists(_.action.toLowerCase.contains(searchText)) ||
        c.vscode.exists(_.action.toLowerCase.contains(searchText)) ||
        c.zed.exists(_.action.toLowerCase.contains(searchText))
      }
    }
    selectedIndex = -1
    scrollOffset = 0
    updateScrollbar()
    canvas.repaint()
  }

  private def updateScrollbar(): Unit = {
    val visibleRows = Math.max(1, (canvas.getHeight - headerHeight) / rowHeight)
    val max         = Math.max(visibleRows, filteredRows.size)
    val clamped     = Math.max(0, Math.min(scrollOffset, max - visibleRows))
    scrollOffset = clamped
    scrollbar.setValues(clamped, visibleRows, 0, max)
    scrollbar.setBlockIncrement(visibleRows)
    scrollbar.setUnitIncrement(1)
  }

  private def isModifierKey(keyCode: Int): Boolean = {
    keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL ||
    keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_META ||
    keyCode == KeyEvent.VK_ALT_GRAPH
  }

  private def buildComboString(e: KeyEvent): String = {
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    if (e.isControlDown) parts += "ctrl"
    if (e.isAltDown) parts += "alt"
    if (e.isShiftDown) parts += "shift"
    if (e.isMetaDown) parts += "meta"

    val keyName = KeyEvent.getKeyText(e.getKeyCode).toLowerCase match {
      case "back_space" | "backspace" => "backspace"
      case "page down" | "page_down" => "pagedown"
      case "page up" | "page_up"     => "pageup"
      case "open bracket"             => "bracketleft"
      case "close bracket"            => "bracketright"
      case "minus"                    => "minus"
      case "equals"                   => "equal"
      case "comma"                    => "comma"
      case "period"                   => "period"
      case "semicolon"                => "semicolon"
      case "slash"                    => "slash"
      case "back_slash" | "back slash" => "backslash"
      case "quote"                    => "quote"
      case "back_quote" | "back quote" => "backquote"
      case s if s.length == 1        => s
      case s                         => s.replaceAll("\\s+", "").toLowerCase
    }

    parts += keyName
    parts.mkString("+")
  }

  private def formatCombo(combo: io.septimalmind.magen.model.Key.KeyCombo): String = {
    val mods = combo.modifiers.map {
      case io.septimalmind.magen.model.Modifier.Ctrl  => "ctrl"
      case io.septimalmind.magen.model.Modifier.Alt   => "alt"
      case io.septimalmind.magen.model.Modifier.Shift => "shift"
      case io.septimalmind.magen.model.Modifier.Meta  => "meta"
    }
    (mods :+ combo.key.name).mkString("+")
  }

  /** Double-buffered Canvas to eliminate flicker. */
  private class TableCanvas extends Canvas {
    enableEvents(java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK)
    private val headerBg: Color   = if (theme.isDark) new Color(0x3A, 0x3A, 0x3A) else new Color(0xD8, 0xD8, 0xD8)
    private val headerFg: Color   = theme.foreground
    private val rowBgEven: Color  = theme.surface
    private val rowBgOdd: Color   = if (theme.isDark) new Color(0x34, 0x34, 0x34) else new Color(0xF5, 0xF5, 0xF5)
    private val selectedBg: Color = if (theme.isDark) new Color(0x26, 0x4F, 0x78) else new Color(0xCC, 0xE8, 0xFF)
    private val gridColor: Color  = if (theme.isDark) new Color(0x50, 0x50, 0x50) else new Color(0xCC, 0xCC, 0xCC)
    private val headerFont: Font  = theme.sansBold(12)
    private val cellFont: Font    = theme.monoFont(12)

    private var offscreen: Image    = _
    private var offscreenW: Int     = 0
    private var offscreenH: Int     = 0

    // Override update() to skip the default clear (eliminates flicker)
    override def update(g: Graphics): Unit = paint(g)

    override def paint(g: Graphics): Unit = {
      val w = getWidth
      val h = getHeight
      if (w <= 0 || h <= 0) return

      // Allocate or resize offscreen buffer
      if (offscreen == null || offscreenW != w || offscreenH != h) {
        offscreen = createImage(w, h)
        offscreenW = w
        offscreenH = h
      }

      val og  = offscreen.getGraphics
      val g2  = og.asInstanceOf[Graphics2D]
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      // Background
      g2.setColor(theme.surface)
      g2.fillRect(0, 0, w, h)

      // Header
      g2.setColor(headerBg)
      g2.fillRect(0, 0, w, headerHeight)
      g2.setFont(headerFont)
      g2.setColor(headerFg)
      var x = theme.scaled(4)
      columns.foreach { col =>
        g2.drawString(col.name, x, headerHeight - theme.scaled(8))
        x += col.width
      }
      g2.setColor(gridColor)
      g2.drawLine(0, headerHeight - 1, w, headerHeight - 1)

      // Rows
      g2.setFont(cellFont)
      val visibleRows = (h - headerHeight) / rowHeight + 1
      val fm          = g2.getFontMetrics

      for (i <- 0 until Math.min(visibleRows, filteredRows.size - scrollOffset)) {
        val rowIdx = scrollOffset + i
        if (rowIdx >= 0 && rowIdx < filteredRows.size) {
          val y = headerHeight + i * rowHeight

          // Row background
          val bg =
            if (rowIdx == selectedIndex) selectedBg
            else if (rowIdx % 2 == 0) rowBgEven
            else rowBgOdd
          g2.setColor(bg)
          g2.fillRect(0, y, w, rowHeight)

          // Cell text
          g2.setColor(theme.foreground)
          x = theme.scaled(4)
          val textY   = y + rowHeight - theme.scaled(6)
          val concept = filteredRows(rowIdx)
          columns.foreach { col =>
            val text    = col.extract(concept)
            val clipped = clipText(fm, text, col.width - theme.scaled(8))
            g2.drawString(clipped, x, textY)
            x += col.width
          }

          // Grid line
          g2.setColor(gridColor)
          g2.drawLine(0, y + rowHeight - 1, w, y + rowHeight - 1)
        }
      }

      // Column dividers
      g2.setColor(gridColor)
      x = 0
      columns.foreach { col =>
        x += col.width
        g2.drawLine(x, 0, x, h)
      }

      og.dispose()

      // Blit offscreen buffer to screen
      g.drawImage(offscreen, 0, 0, null)

      // Update scrollbar on resize
      updateScrollbar()
    }

    private def clipText(fm: FontMetrics, text: String, maxWidth: Int): String = {
      if (fm.stringWidth(text) <= maxWidth) return text
      var end = text.length
      while (end > 0 && fm.stringWidth(text.substring(0, end) + "...") > maxWidth) {
        end -= 1
      }
      if (end <= 0) "..." else text.substring(0, end) + "..."
    }
  }
}
