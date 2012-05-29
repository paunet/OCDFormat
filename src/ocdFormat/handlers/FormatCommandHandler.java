package ocdFormat.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class FormatCommandHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {

    ISelection selection = HandlerUtil.getCurrentSelection(event);
    if (selection == null) {
      return null;
    }

    TextSelection textSelection = (TextSelection)selection;
    if (textSelection == null || !(textSelection instanceof TextSelection) ) {
      return null;
    }

    IEditorPart activeEditor = HandlerUtil.getActiveEditor(event);
    if (activeEditor == null || !(activeEditor instanceof ITextEditor) ) {
      return null;
    }

    IDocumentProvider p = ((ITextEditor) activeEditor).getDocumentProvider();
    if (p == null) {
      return null;
    }

    IDocument document = p.getDocument(activeEditor.getEditorInput());
    if (document == null) {
      return null;
    }

    try {
      String result = textSelection.getText();
      if (result.indexOf('\n') == -1) {
        return null;
      }
      if (result.indexOf('=') == -1) {
        result = getTextWithColumnsAligned(result);
      }
      else {
        result = getTextWithEqualsAligned(result);
      }

      document.replace(textSelection.getOffset(), textSelection.getLength(), result);
    }
    catch (BadLocationException e) {
      return null;
    }

    return null;
  }

  private String getTextWithColumnsAligned(String text) {
    List<List<String>> items = new ArrayList<List<String>>();
    List<Integer> maxes = new ArrayList<Integer>();

    String[] lines  = text.split("\n");
    Pattern pattern = Pattern.compile("(\\s*)(\\S+)\\s*");

    for (String line : lines) {
      List<String> cols = new ArrayList<String>();
      Matcher matcher = pattern.matcher(line);
      int i = 0;
      while (matcher.find()) {
        if (i == 0) {
          //for convenience the leading whitespace goes into col 1
          cols.add(matcher.group(1));
          maxes.add(0);
          i++;
        }
        String item = matcher.group(2);
        cols.add(item);
        if (i == maxes.size()) {
          maxes.add(0);
        }
        int length = item.length();
        if (length > maxes.get(i)) {
          maxes.set(i, length);
        }
        i++;
      }
      items.add(cols);

    }

    StringBuilder sb = new StringBuilder();
    for (List<String> cols : items) {
      int i = 0;
      for (String item : cols) {
        sb.append(item);
        if (i != 0) {
          Integer max = maxes.get(i);
          for (int j = item.length(); j <= max; j++) {
            sb.append(" ");
          }
        }
        i++;
      }
      sb.append("\n");
    }

    if (text.endsWith("\n")) {
      sb.append("\n");
    }

    return sb.toString();
  }

  public String getTextWithEqualsAligned(String text) {

    String[] lines     = text.split("\n");
    List<Thing> things = new ArrayList<Thing>(lines.length);

    int maxLHSPre = buildThingList(lines, things);

    int maxLHS = convertForm2ToForm1(things, maxLHSPre);

    StringBuilder resultStringBuilder = buildResultStringBuilder(things, maxLHS);

    if (text.endsWith("\n")) {
      resultStringBuilder.append("\n");
    }

    return resultStringBuilder.toString();

  }

  private StringBuilder buildResultStringBuilder(List<Thing> things, int maxLHS) {

    StringBuilder sb = new StringBuilder();

    //now we only need to work on correcting space for LHS
    boolean first = true;
    for (Thing thing : things) {

      if (first) {
        first = false;
      }
      else {
        sb.append("\n");
      }
      if (thing.line != null) {
        sb.append(thing.line);
        continue;
      }
      sb.append(thing.LeadingWhitespace);

      sb.append(thing.LHS);
      for (int i = thing.LHS.length(); i < maxLHS; i++) {
        sb.append(" ");
      }
      sb.append(" = ");
      sb.append(thing.RHS);
    }
    return sb;
  }

  private int convertForm2ToForm1(List<Thing> things, int maxLHSPre) {
    int maxLHS = 0;
    //go through all the Things - if we find FORM 2: xxx yyy = zzz;
    //then correct the space between xxx and yyy and add xxx to LHS
    //effectively convertine FORM2 to FORM1 xxx = zzz;
    for (Thing thing : things) {
      if (thing.line != null) {
        continue;
      }
      if (thing.LHSPre.length() > 0) {
        for (int i = thing.LHSPre.length(); i <= maxLHSPre; i++) {
          thing.LHS = " " + thing.LHS;
        }
        thing.LHS = thing.LHSPre + thing.LHS;
      }
      if (thing.LHS.length() > maxLHS) {
        maxLHS = thing.LHS.length();
      }
    }
    return maxLHS;
  }

  private int buildThingList(String[] lines, List<Thing> things) {
    int     maxLHSPre = 0;
    Pattern pattern   = Pattern.compile("(\\s*)(\\S+)(\\s*)(\\S*) *=(.*)");

    for (String line : lines) {

      Thing t = new Thing();
      if (hasMultipleEqualsSigns(line)) {
        //ignore lines with > 1 equals signs
        t.line = line;
      }
      else {
        Matcher m = pattern.matcher(line);
        if (m.find()) {
          //          for (int i = 1; i <= m.groupCount(); i++) {
          //            System.out.println("-" + m.group(i) + "-");
          //          }
          //          System.exit(0);
          t.LeadingWhitespace = m.group(1);
          t.RHS               = m.group(5).trim();
          if (m.group(4).length() == 0) {
            //FORM 1: xxx = zzz;
            t.LHSPre = "";
            t.LHS    = m.group(2).trim();
          }
          else {
            //FORM 2:  xxx yyy = zzz;
            t.LHSPre = m.group(2).trim();
            t.LHS    = m.group(4).trim();
          }

          if (t.LHSPre.length() > maxLHSPre) {
            maxLHSPre = t.LHSPre.length();
          }

        }
        else {
          //if it's not an assignment statement, just duplicate the line
          t.line = line;
        }
      }
      things.add(t);
    }
    return maxLHSPre;
  }

  private boolean hasMultipleEqualsSigns(String line) {
    return line.indexOf('=') == -1 || line.indexOf('=') != line.lastIndexOf('=');
  }

  private class Thing {
    String LeadingWhitespace;
    String LHSPre;
    String LHS;
    String RHS;
    String line;
  }

  public static void main(String[] args) {
    String txt ="ass=ass;\na = a";
    System.out.println(new FormatCommandHandler().getTextWithEqualsAligned(txt));
    System.out.println();

    txt = "PeriodicUpdateWindow.parent = parent;\n" +
        "PeriodicUpdateWindow.as = new AnalyticsSuite();";
    System.out.println(new FormatCommandHandler().getTextWithEqualsAligned(txt));
    System.out.println();

    txt = "    updateMode             = \"\";\n" +
        "    lastProcessedMessage   = -1;\n" +
        "//sdkfljslkjdflsdjfkjsdkfjsldfkdfjlskdjfldk\n" +
        "    int puServerIsUp        = false;\n" +
        "    String puSuccessSinceEntering = false;\n";
    System.out.println(new FormatCommandHandler().getTextWithEqualsAligned(txt));
    System.out.println();

    txt = "Component component = this.lblProgressMain;\n" +
        "Componentsss co  aaaa;\n" +
        "nstraints.gridx = 0;";
    System.out.println(new FormatCommandHandler().getTextWithEqualsAligned(txt));
    System.out.println();

    txt = "  aa bb cc dd;\n" +
        "  aaa b    cccc;\n" +
        "  a bbbb c d;";
    System.out.println(new FormatCommandHandler().getTextWithColumnsAligned(txt));
    System.out.println();
  }

}