/*
 * @author max
 */
package org.jetbrains.jet.plugin.internal.codewindow;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Alarm;
import org.jetbrains.jet.codegen.ClassBuilderFactory;
import org.jetbrains.jet.codegen.ClassFileFactory;
import org.jetbrains.jet.codegen.GenerationState;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzingUtils;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.plugin.compiler.WholeProjectAnalyzerFacade;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class BytecodeToolwindow extends JPanel {
    private static final int UPDATE_DELAY = 500;
    private final Editor myEditor;
    private final Alarm myUpdateAlarm;
    private Location myCurrentLocation;
    private final Project myProject;


    public BytecodeToolwindow(Project project) {
        super(new BorderLayout());
        myProject = project;
        myEditor = EditorFactory.getInstance().createEditor(EditorFactory.getInstance().createDocument(""), project, JavaFileType.INSTANCE, true);
        add(myEditor.getComponent());
        myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
        myUpdateAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
                myUpdateAlarm.addRequest(this, UPDATE_DELAY);
                Location location = Location.fromEditor(FileEditorManager.getInstance(myProject).getSelectedTextEditor());
                if (!Comparing.equal(location, myCurrentLocation)) {
                    updateBytecode(location, myCurrentLocation);
                    myCurrentLocation = location;
                }
            }
        }, UPDATE_DELAY);
    }

    private void updateBytecode(Location location, Location oldLocation) {
        Editor editor = location.editor;

        if (editor == null) {
            setText("");
        }
        else {
            VirtualFile vFile = ((EditorEx) editor).getVirtualFile();
            if (vFile == null) {
                setText("");
                return;
            }

            PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
            if (!(psiFile instanceof JetFile)) {
                setText("");
                return;
            }

            if (oldLocation == null || !Comparing.equal(oldLocation.editor, location.editor) || oldLocation.modificationStamp != location.modificationStamp) {
                setText(generateToText((JetFile) psiFile));
            }

            Document document = editor.getDocument();
            int startLine = document.getLineNumber(location.startOffset);
            int endLine = document.getLineNumber(location.endOffset);
            if (endLine > startLine && location.endOffset > 0 && document.getCharsSequence().charAt(location.endOffset - 1) == '\n') endLine--;

            Document byteCodeDocument = myEditor.getDocument();
            Pair<Integer, Integer> linesRange = mapLines(byteCodeDocument.getText(), startLine, endLine);

            int startOffset = byteCodeDocument.getLineStartOffset(linesRange.first);
            int endOffset = Math.min(byteCodeDocument.getLineStartOffset(linesRange.second + 1), byteCodeDocument.getTextLength());
            myEditor.getCaretModel().moveToOffset(endOffset);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            myEditor.getCaretModel().moveToOffset(startOffset);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

            myEditor.getSelectionModel().setSelection(startOffset,
                                                      endOffset);
        }
    }

    private static Pair<Integer, Integer> mapLines(String text, int startLine, int endLine) {
        int byteCodeLine = 0;
        int byteCodeStartLine = -1;
        int byteCodeEndLine = -1;

        List<Integer> lines = new ArrayList<Integer>();
        for (String line : text.split("\n")) {
            line = line.trim();

            if (line.startsWith("LINENUMBER")) {
                int ktLineNum = new Scanner(line.substring("LINENUMBER".length())).nextInt() - 1;
                lines.add(ktLineNum);
            }
        }
        Collections.sort(lines);

        for (Integer line : lines) {
            if (line >= startLine) {
                startLine = line;
                break;
            }
        }
        
        for (String line : text.split("\n")) {
            line = line.trim();

            if (line.startsWith("LINENUMBER")) {
                int ktLineNum = new Scanner(line.substring("LINENUMBER".length())).nextInt() - 1;

                if (byteCodeStartLine < 0 && ktLineNum == startLine) {
                    byteCodeStartLine = byteCodeLine;
                }

                if (byteCodeStartLine > 0&& ktLineNum > endLine) {
                    byteCodeEndLine = byteCodeLine - 1;
                    break;
                }
            }

            if (byteCodeStartLine >= 0 && (line.startsWith("MAXSTACK") || line.startsWith("LOCALVARIABLE") || line.isEmpty())) {
                byteCodeEndLine = byteCodeLine - 1;
                break;
            }


            byteCodeLine++;
        }



        if (byteCodeStartLine == -1 || byteCodeEndLine == -1) {
            return new Pair<Integer, Integer>(0, 0);
        }
        else {
            return new Pair<Integer, Integer>(byteCodeStartLine, byteCodeEndLine);
        }

    }

    private void setText(final String text) {
        new WriteCommandAction(myProject) {
            @Override
            protected void run(Result result) throws Throwable {
                myEditor.getDocument().setText(text);
            }
        }.execute();
    }

    protected String generateToText(JetFile file) {
        GenerationState state = new GenerationState(myProject, ClassBuilderFactory.TEXT);
        try {
            BindingContext binding = WholeProjectAnalyzerFacade.analyzeProjectWithCacheOnAFile(file);
            AnalyzingUtils.throwExceptionOnErrors(binding);
            state.compileCorrectNamespaces(binding, Collections.singletonList(file.getRootNamespace()));
        } catch (Exception e) {
            StringWriter out = new StringWriter(1024);
            e.printStackTrace(new PrintWriter(out));
            return out.toString();
        }


        StringBuilder answer = new StringBuilder();

        final ClassFileFactory factory = state.getFactory();
        for (String filename : factory.files()) {
            answer.append("// ================ ");
            answer.append(filename);
            answer.append(" =================\n");
            answer.append(factory.asText(filename)).append("\n\n");
        }

        return answer.toString();
    }
    

    public static class Factory implements ToolWindowFactory {
        @Override
        public void createToolWindowContent(Project project, ToolWindow toolWindow) {
            toolWindow.getContentManager().addContent(ContentFactory.SERVICE.getInstance().createContent(new BytecodeToolwindow(project), "", false));
        }
    }
    
    public static class Location {
        final Editor editor;
        final long modificationStamp;
        final int startOffset;
        final int endOffset;

        private Location(Editor editor) {
            this.editor = editor;
            modificationStamp = editor != null ? editor.getDocument().getModificationStamp() : 0;
            startOffset = editor != null ? editor.getSelectionModel().getSelectionStart() : 0;
            endOffset = editor != null ? editor.getSelectionModel().getSelectionEnd() : 0;
        }

        public static Location fromEditor(Editor editor) {
            return new Location(editor);
        }

        public Editor getEditor() {
            return editor;
        }

        public long getModificationStamp() {
            return modificationStamp;
        }

        public int getStartOffset() {
            return startOffset;
        }

        public int getEndOffset() {
            return endOffset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Location)) return false;

            Location location = (Location) o;

            if (endOffset != location.endOffset) return false;
            if (modificationStamp != location.modificationStamp) return false;
            if (startOffset != location.startOffset) return false;
            if (editor != null ? !editor.equals(location.editor) : location.editor != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = editor != null ? editor.hashCode() : 0;
            result = 31 * result + (int) (modificationStamp ^ (modificationStamp >>> 32));
            result = 31 * result + startOffset;
            result = 31 * result + endOffset;
            return result;
        }
    }    
}
