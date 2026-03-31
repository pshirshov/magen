package io.septimalmind.magen.gui

import java.awt.{Color, Font, GraphicsEnvironment, Toolkit}
import java.nio.file.{Files, Paths}

/** Detects system dark mode and HiDPI scale, provides themed colors and scaled fonts.
  *
  * All AWT types (Color, Font) are lazy to avoid build-time initialization in native-image.
  */
object GuiTheme {
  lazy val isDark: Boolean = detectDarkMode()
  lazy val uiScale: Double = Math.min(4.0, Math.ceil(detectScale()))

  // -- Colors (lazy to stay out of native-image build-time heap) --
  lazy val background: Color    = if (isDark) new Color(0x2B, 0x2B, 0x2B) else new Color(0xF0, 0xF0, 0xF0)
  lazy val surface: Color       = if (isDark) new Color(0x3C, 0x3C, 0x3C) else Color.WHITE
  lazy val surfaceAlt: Color    = if (isDark) new Color(0x33, 0x33, 0x2B) else new Color(0xFF, 0xFF, 0xE0)
  lazy val foreground: Color    = if (isDark) new Color(0xDD, 0xDD, 0xDD) else Color.BLACK
  lazy val foregroundDim: Color = if (isDark) new Color(0xAA, 0xAA, 0xAA) else new Color(0x44, 0x44, 0x44)
  lazy val controlBg: Color     = if (isDark) new Color(0x4A, 0x4A, 0x4A) else new Color(0xE0, 0xE0, 0xE0)
  lazy val statusBg: Color      = if (isDark) new Color(0x38, 0x38, 0x38) else new Color(0xE0, 0xE0, 0xE0)

  // -- Fonts (scaled for HiDPI) --
  def monoFont(size: Int): Font = new Font(Font.MONOSPACED, Font.PLAIN, scaled(size))
  def sansFont(size: Int): Font = new Font(Font.SANS_SERIF, Font.PLAIN, scaled(size))
  def sansBold(size: Int): Font = new Font(Font.SANS_SERIF, Font.BOLD, scaled(size))

  def scaled(value: Int): Int = Math.round(value * uiScale).toInt

  private def detectDarkMode(): Boolean = {
    // 1. Check GTK color-scheme via gsettings (GNOME)
    runCommand("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
      .foreach(o => if (o.contains("dark")) return true)

    // 2. Check GTK theme name
    runCommand("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
      .map(_.toLowerCase)
      .foreach(o => if (o.contains("dark")) return true)

    // 3. Check GTK3 settings.ini
    val gtkSettings = Paths.get(System.getProperty("user.home", ""), ".config", "gtk-3.0", "settings.ini")
    if (Files.exists(gtkSettings)) {
      try {
        val content = new String(Files.readAllBytes(gtkSettings)).toLowerCase
        if (content.contains("prefer-dark") || content.contains("gtk-application-prefer-dark-theme=true")
            || content.contains("gtk-theme-name") && content.contains("dark")) {
          return true
        }
      } catch {
        case _: Exception =>
      }
    }

    false
  }

  private def detectScale(): Double = {
    def found(method: String, value: Double): Double = {
      System.err.println(s"GuiTheme: scale detected via $method: $value")
      value
    }

    // 1. Explicit sun.java2d.uiScale property (highest priority)
    sysPropDouble("sun.java2d.uiScale").foreach(s => return found("sun.java2d.uiScale", s))

    // 2. GDK_SCALE env var
    envDouble("GDK_SCALE").foreach(s => return found("GDK_SCALE", s))

    // 3. Java2D graphics transform (works when JVM-level scaling is active)
    try {
      val ge        = GraphicsEnvironment.getLocalGraphicsEnvironment
      val gd        = ge.getDefaultScreenDevice
      val transform = gd.getDefaultConfiguration.getDefaultTransform
      val scaleX    = transform.getScaleX
      if (scaleX > 1.0) return found("GraphicsConfig.transform", scaleX)
    } catch {
      case _: Exception =>
    }

    // 4. GNOME monitors.xml (most reliable for fractional scaling on Wayland)
    detectGnomeMonitorsXml().foreach(s => return found("monitors.xml", s))

    // 5. GNOME Mutter scale via gdbus
    detectMutterScale().foreach(s => return found("Mutter D-Bus", s))

    // 7. GNOME scaling-factor (integer scaling)
    runCommand("gsettings", "get", "org.gnome.desktop.interface", "scaling-factor")
      .flatMap(parseUintFromGsettings)
      .filter(_ > 1)
      .foreach(s => return found("gsettings scaling-factor", s.toDouble))

    // 8. Xft.dpi from X resources
    runCommand("xrdb", "-query").foreach {
      output =>
        val pattern = """Xft\.dpi:\s*(\d+)""".r
        pattern.findFirstMatchIn(output).foreach {
          m =>
            val dpi = m.group(1).toInt
            if (dpi > 120) return found(s"Xft.dpi=$dpi", dpi.toDouble / 96.0)
        }
    }

    // 9. GNOME text-scaling-factor
    runCommand("gsettings", "get", "org.gnome.desktop.interface", "text-scaling-factor")
      .flatMap(
        s =>
          try Some(s.trim.toDouble)
          catch { case _: Exception => None }
      )
      .filter(_ > 1.0)
      .foreach(s => return found("gsettings text-scaling-factor", s))

    // 10. Physical DPI from xrandr
    detectXrandrScale().foreach(s => return found("xrandr physical DPI", s))

    // 11. Toolkit DPI (often lies on XWayland, but last resort)
    try {
      val dpi = Toolkit.getDefaultToolkit.getScreenResolution
      if (dpi > 120) return found(s"Toolkit.screenResolution=$dpi", dpi.toDouble / 96.0)
    } catch {
      case _: Exception =>
    }

    System.err.println("GuiTheme: no scale detected, using 1.0")
    1.0
  }

  /** Read the active monitor scale from GNOME's ~/.config/monitors.xml.
    * This is the most reliable source for fractional scaling on GNOME Wayland.
    */
  private def detectGnomeMonitorsXml(): Option[Double] = {
    try {
      val configFile = Paths.get(System.getProperty("user.home", ""), ".config", "monitors.xml")
      if (!Files.exists(configFile)) return None

      val content = new String(Files.readAllBytes(configFile))
      // Extract <scale>N.N</scale> from the configuration
      val scalePattern = """<scale>(\d+\.?\d*)</scale>""".r
      val scales = scalePattern.findAllMatchIn(content).map(_.group(1).toDouble).toList

      // Return the first scale > 1.0 (the primary/active monitor)
      scales.find(s => s > 1.0 && s <= 4.0)
    } catch {
      case _: Exception => None
    }
  }

  /** Query Mutter's DisplayConfig D-Bus API for the monitor scale. */
  private def detectMutterScale(): Option[Double] = {
    try {
      val output = runCommand(
        "gdbus",
        "call",
        "--session",
        "--dest",
        "org.gnome.Mutter.DisplayConfig",
        "--object-path",
        "/org/gnome/Mutter/DisplayConfig",
        "--method",
        "org.gnome.Mutter.DisplayConfig.GetCurrentState",
      ).getOrElse(return None)

      // Match 'scale': <1.5> or 'scale': <double 1.7500>
      val scalePattern = """'scale':\s*<(?:double )?(\d+\.\d+)>""".r
      val scales       = scalePattern.findAllMatchIn(output).map(_.group(1).toDouble).toList

      // Only accept scales in the sane range 1.0 < s <= 4.0
      scales.filter(s => s > 1.0 && s <= 4.0).headOption
    } catch {
      case _: Exception => None
    }
  }

  /** Parse physical monitor DPI from xrandr output. */
  private def detectXrandrScale(): Option[Double] = {
    try {
      val output = runCommand("xrandr").getOrElse(return None)
      // Match lines like: "3840x2160+0+0 ... 697mm x 392mm"
      val pattern = """(\d{3,5})x(\d{3,5})\+\d+\+\d+.*?(\d+)mm\s*x\s*(\d+)mm""".r
      pattern.findFirstMatchIn(output).flatMap {
        m =>
          val pxW = m.group(1).toDouble
          val mmW = m.group(3).toDouble
          if (mmW > 0) {
            val dpi = pxW / (mmW / 25.4)
            if (dpi > 120) Some(dpi / 96.0)
            else None
          } else None
      }
    } catch {
      case _: Exception => None
    }
  }

  private def parseUintFromGsettings(s: String): Option[Int] = {
    // gsettings outputs "uint32 2" or just "2" - extract the LAST number
    val pattern = """(\d+)\s*$""".r
    pattern.findFirstMatchIn(s.trim).map(_.group(1).toInt)
  }

  private def runCommand(cmd: String*): Option[String] = {
    try {
      val proc = new ProcessBuilder(cmd: _*)
        .redirectErrorStream(true).start()
      val output = new String(proc.getInputStream.readAllBytes()).trim
      proc.waitFor()
      if (proc.exitValue() == 0 && output.nonEmpty) Some(output) else None
    } catch {
      case _: Exception => None
    }
  }

  private def sysPropDouble(key: String): Option[Double] = {
    val v = System.getProperty(key)
    if (v != null)
      try Some(v.toDouble)
      catch { case _: NumberFormatException => None }
    else None
  }

  private def envDouble(key: String): Option[Double] = {
    val v = System.getenv(key)
    if (v != null)
      try Some(v.toDouble)
      catch { case _: NumberFormatException => None }
    else None
  }
}
