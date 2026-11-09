import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.IStore;
import se.yrell.developertools.runtime.AutoFieldIdAllocator;

/** Regression for 0.1.49-0.1.52: absent/unreadable AR metadata must not disable IDs. */
public final class AutoFieldIdRegressionTest {
    private static int checks;
    private static final Method NEXT;
    private static final Method REMEMBER;
    static {
        try {
            NEXT = AutoFieldIdAllocator.class.getDeclaredMethod("calculateNextId", IFormObject.class, int.class);
            NEXT.setAccessible(true);
            REMEMBER = AutoFieldIdAllocator.class.getDeclaredMethod("rememberAssignedId", int.class, int.class, IFormObject.class);
            REMEMBER.setAccessible(true);
        } catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
    }

    public static void main(String[] args) throws Exception {
        int developer = 11;
        LocalDate today = LocalDate.now();
        int first = Integer.parseInt(String.format("%02d%02d%02d%02d%02d", developer,
                today.getYear() % 100, today.getMonthValue(), today.getDayOfMonth(), 1));
        // Missing IEntryStore/ARServerUser is a realistic offline/restricted-API case.
        // In v0.1.49 this returned -1 and normal Studio IDs were silently used.
        IFormObject restricted = form("no-metadata-a", List.of());
        verify(next(restricted, developer) == first,
                "metadata-unavailable fallback must allocate first valid ID");

        IFormObject existing = form("no-metadata-b", List.of(first));
        verify(next(existing, developer) == first + 1,
                "existing current-form ID must not be reused");

        IFormObject session = form("no-metadata-c", List.of());
        REMEMBER.invoke(null, first, developer, session);
        verify(next(session, developer) == first + 1,
                "session-allocated ID must not be reused");

        IFormObject combined = form("no-metadata-d", List.of(first, first + 1));
        REMEMBER.invoke(null, first + 2, developer, combined);
        verify(next(combined, developer) == first + 3,
                "both current-form and in-session IDs must be excluded");

        verify(next(form("no-metadata-e", List.of()), 21) > 0,
                "highest supported developer ID still produces an int");
        System.out.println("Auto Field ID regression tests: " + checks + " PASS");
    }

    private static void verify(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static int next(IFormObject form, int developer) throws Exception {
        return (int) NEXT.invoke(null, form, developer);
    }

    private static IFormObject form(String storeName, List<Integer> usedIds) {
        IStore store = (IStore) Proxy.newProxyInstance(IStore.class.getClassLoader(),
                new Class<?>[] {IStore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return storeName;
                    return defaultValue(method.getReturnType());
                });
        Collection<IFieldObject> fields = new ArrayList<>();
        for (int id : usedIds) {
            IFieldObject field = (IFieldObject) Proxy.newProxyInstance(IFieldObject.class.getClassLoader(),
                    new Class<?>[] {IFieldObject.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getFieldID")) return id;
                        return defaultValue(method.getReturnType());
                    });
            fields.add(field);
        }
        return (IFormObject) Proxy.newProxyInstance(IFormObject.class.getClassLoader(),
                new Class<?>[] {IFormObject.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getStore" -> store;
                        case "getFields" -> fields;
                        case "getDeletedFields" -> List.of();
                        case "getName" -> "TEST:AutoFieldId";
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
