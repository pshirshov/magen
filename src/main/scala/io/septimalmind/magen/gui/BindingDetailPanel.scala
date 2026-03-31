package io.septimalmind.magen.gui

import io.septimalmind.magen.model.Concept

import java.awt.*

/** Panel showing details of a selected binding concept, with editable fields. */
class BindingDetailPanel extends Panel {
  private val theme = GuiTheme

  private val conceptLabel  = new Label("(no selection)")
  private val bindingField  = new TextField(40)
  private val ideaField     = new TextField(40)
  private val vscodeField   = new TextField(40)
  private val zedField      = new TextField(40)

  init()

  private def init(): Unit = {
    setLayout(new GridLayout(5, 2, theme.scaled(4), theme.scaled(2)))
    setBackground(theme.background)

    addRow("Concept:", conceptLabel)
    addEditRow("Binding:", bindingField)
    addEditRow("IDEA:", ideaField)
    addEditRow("VSCode:", vscodeField)
    addEditRow("Zed:", zedField)

    conceptLabel.setForeground(theme.foreground)
    conceptLabel.setFont(theme.sansBold(13))
  }

  def showConcept(concept: Concept): Unit = {
    conceptLabel.setText(concept.id)
    bindingField.setText(
      concept.binding.toList
        .map(chord => chord.combos.map(formatCombo).mkString(" "))
        .mkString(" / ")
    )
    ideaField.setText(concept.idea.map(_.action).getOrElse(""))
    vscodeField.setText(concept.vscode.map(_.action).getOrElse(""))
    zedField.setText(concept.zed.map(_.action).getOrElse(""))
  }

  def clear(): Unit = {
    conceptLabel.setText("(no selection)")
    bindingField.setText("")
    ideaField.setText("")
    vscodeField.setText("")
    zedField.setText("")
  }

  private def addRow(labelText: String, component: Component): Unit = {
    val label = new Label(labelText, Label.RIGHT)
    label.setFont(theme.sansFont(12))
    label.setForeground(theme.foregroundDim)
    add(label)
    add(component)
  }

  private def addEditRow(labelText: String, field: TextField): Unit = {
    field.setFont(theme.monoFont(12))
    field.setBackground(theme.surface)
    field.setForeground(theme.foreground)
    addRow(labelText, field)
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
}
