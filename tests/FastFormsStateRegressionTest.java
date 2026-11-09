import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import se.yrell.developertools.fastagent.FastFormsBridge;

public class FastFormsStateRegressionTest {
    private static int passed;

    private static Set<?> values() throws Exception {
        Method method = FastFormsBridge.class.getDeclaredMethod("allowedValuesFromCheckboxState");
        method.setAccessible(true);
        return (Set<?>) method.invoke(null);
    }

    private static void check(String name, Set<?> expected) throws Exception {
        Set<?> actual = values();
        if (!expected.equals(actual)) throw new AssertionError(name + " expected " + expected + " got " + actual);
        passed++;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("bmc.ds.fastForms.enabled", "true");
        System.setProperty("bmc.ds.fastForms.values", "2,4");
        System.setProperty("bmc.ds.fastForms.allowManualBase", "true");
        check("configured default", Set.of(2, 4));
        FastFormsBridge.sanitizeSelectedCheckboxNames(Arrays.asList("Overlay", "Custom"));
        check("overlay custom", Set.of(2, 4));
        FastFormsBridge.sanitizeSelectedCheckboxNames(Arrays.asList("Overlay", "Custom", "Base"));
        check("manual Base", Set.of(0, 2, 4));
        FastFormsBridge.sanitizeSelectedCheckboxNames(Collections.emptyList());
        check("explicitly unchecked all", Collections.emptySet());
        FastFormsBridge.sanitizeSelectedCheckboxNames(List.of("Overlaid"));
        check("disallowed selection must not revert to defaults", Collections.emptySet());
        FastFormsBridge.sanitizeSelectedCheckboxNames(List.of("Overlay"));
        check("re-enable Overlay", Set.of(2));
        System.out.println("PASS: " + passed + " Fast Forms checkbox state assertions");
    }
}
