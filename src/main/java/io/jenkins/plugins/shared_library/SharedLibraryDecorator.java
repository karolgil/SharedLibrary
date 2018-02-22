package io.jenkins.plugins.shared_library;

import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.Message;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

@Extension public class SharedLibraryDecorator extends GroovyShellDecorator {
  @Override public void customizeImports(CpsFlowExecution execution, ImportCustomizer ic) {
    ic.addImports(SharedLibrary.class.getName());
  }

  @Override public void configureCompiler(final CpsFlowExecution execution, CompilerConfiguration cc) {
    if (execution == null) {
      // TODO cannot inject libraries during form validation.
      // Adder could have a method to look up libraries from the last build,
      // but the current GroovyShellDecorator API does not allow us to even detect the Job!
      return;
    }
    cc.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CONVERSION) {
      @Override public void call(final SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        final List<String> libraries = new ArrayList<>();
        new ClassCodeVisitorSupport() {
          @Override protected SourceUnit getSourceUnit() {
            return source;
          }
          @Override public void visitAnnotations(AnnotatedNode node) {
            super.visitAnnotations(node);
            for (AnnotationNode annotationNode : node.getAnnotations()) {
              String name = annotationNode.getClassNode().getName();
              if (name.equals(SharedLibrary.class.getCanonicalName()) ||
                  // In the CONVERSION phase we will not have resolved the implicit import yet.
                  name.equals(SharedLibrary.class.getSimpleName())) {
                Expression path = annotationNode.getMember("value");
                if (path == null) {
                  source.getErrorCollector().addErrorAndContinue(Message.create("@SharedLibrary was missing a path", source));
                } else {
                  processExpression(source, libraries, path);
                }
              }
            }
          }

          private void processExpression(SourceUnit source, List<String> libraries, Expression path) {
            if (path instanceof ConstantExpression) { // one library
              Object constantValue = ((ConstantExpression) path).getValue();
              if (constantValue instanceof String) {
                libraries.add((String) constantValue);
              } else {
                source.getErrorCollector().addErrorAndContinue(Message.create("@SharedLibrary value ‘" + constantValue + "’ was not a string", source));
              }
            } else {
              source.getErrorCollector().addErrorAndContinue(Message.create("@SharedLibrary value ‘" + path.getText() + "’ was not a constant; did you mean to use the ‘library’ step instead?", source));
            }
          }
        }.visitClass(classNode);
        try {
          TaskListener listener = execution.getOwner().getListener();
          if (libraries.isEmpty()) {
            listener.getLogger().println("[SharedLibrary] Could not find any definition of SharedLibrary");
            return;
          }
          final String finalPath = getFinalSharedLibraryPath(libraries, execution);
          listener.getLogger().println("[SharedLibrary] Loading " + finalPath + " to classpath... ");
          execution.getTrustedShell().getClassLoader().addClasspath(finalPath);
          listener.getLogger().print("done\n");
        } catch (Exception x) {
          // Merely throwing CompilationFailedException does not cause compilation to…fail. Gotta love Groovy!
          source.getErrorCollector().addErrorAndContinue(Message.create("Loading shared libraries failed", source));
          try {
            TaskListener listener = execution.getOwner().getListener();
            if (x instanceof AbortException) {
              listener.error(x.getMessage());
            } else {
              listener.getLogger().println(Functions.printThrowable(x).trim()); // TODO 2.43+ use Functions.printStackTrace
            }
            throw new CompilationFailedException(Phases.CONVERSION, source);
          } catch (IOException x2) {
            Logger.getLogger(SharedLibraryDecorator.class.getName()).log(Level.WARNING, null, x2);
            throw new CompilationFailedException(Phases.CONVERSION, source, x); // reported at least in Jenkins 2
          }
        }
      }
    });
  }

  private String getFinalSharedLibraryPath(List<String> libraries, CpsFlowExecution execution) throws IOException {
    String url = execution.getUrl(); // e.g. "job/Directory/job/JobName/34/execution/"
    String[] parts = url.substring(4).split("/job/");
    parts[parts.length-1] = parts[parts.length-1].split("/")[0];
    String partialPath = StringUtils.join(parts, "/");
    return System.getenv("JENKINS_HOME") + "/workspace/" + partialPath + "@script/" + libraries.get(0);
  }
}
