/**
 * Runs in a fresh JVM with -javaagent and -Xverify:all. This checks the real
 * BMC save classes can be verified after bytecode instrumentation, not merely
 * that an ASM visitor emitted instructions.
 */
public class AgentMessageClassloadRegressionTest {
    public static void main(String[] args) throws Exception {
        String[] names = {
            "com.bmc.arsys.studio.commonui.common.info.Info",
            "com.bmc.arsys.studio.ui.editors.form.FormEditor",
            "com.bmc.arsys.studio.documenteditor.common.documenteditor.DocumentPage"
        };
        for (String name : names) {
            Class.forName(name, false, AgentMessageClassloadRegressionTest.class.getClassLoader());
            if (!Boolean.getBoolean("se.yrell.developertools.messageSuppressionAgent." + name + ".active")) {
                throw new AssertionError("Agent did not transform " + name);
            }
        }
        System.out.println("AgentMessageClassloadRegressionTest PASS " + names.length);
    }
}
