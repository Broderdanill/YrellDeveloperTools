import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.bmc.arsys.api.ColumnFieldLimit;
import com.bmc.arsys.api.DataType;
import com.bmc.arsys.api.FieldLimit;
import com.bmc.arsys.api.TableFieldLimit;
import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;
import se.yrell.developertools.runtime.DefaultNameRuntime;

public final class DefaultNamingRegressionTest {
    private static final int TABLE_ID = 536870100;
    private static final int COLUMN_ID = 536870101;
    private static final Method CHECK;
    private static int assertions;

    static {
        try {
            CHECK = DefaultNameRuntime.class.getDeclaredMethod(
                    "isVerifiedTableColumn", IFormObject.class, IFieldObject.class, IFieldObject.class);
            CHECK.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws Exception {
        assertEquals("CONTROL is 32", 32, DataType.CONTROL.toInt());
        assertEquals("TABLE is 33", 33, DataType.TABLE.toInt());
        assertEquals("COLUMN is 34", 34, DataType.COLUMN.toInt());

        IFieldObject table = field(TABLE_ID, DataType.TABLE.toInt(), new TableFieldLimit());
        IFieldObject control = field(COLUMN_ID, DataType.CONTROL.toInt(), new ColumnFieldLimit(TABLE_ID, 0, 0, 0));
        IFieldObject column = field(COLUMN_ID, DataType.COLUMN.toInt(), new ColumnFieldLimit(TABLE_ID, 0, 0, 0));
        Map<Integer, IFieldObject> withTable = new HashMap<>();
        withTable.put(TABLE_ID, table);
        IFormObject form = form(withTable);

        assertFalse("BUTTON/CONTROL must never be renamed even if the limit resembles a column", form, control, table);
        assertTrue("Real COLUMN with explicit parent TABLE", form, column, table);
        assertTrue("Real COLUMN with parent resolved from FORM", form, column, null);
        assertFalse("A COLUMN with no parent relation is not eligible", form,
                field(COLUMN_ID, DataType.COLUMN.toInt(), new ColumnFieldLimit()), table);
        assertFalse("A COLUMN without ColumnFieldLimit is not eligible", form,
                field(COLUMN_ID, DataType.COLUMN.toInt(), null), table);
        assertFalse("A TABLE itself must never be renamed", form, table, null);
        assertFalse("A PAGE must never be renamed", form,
                field(COLUMN_ID, DataType.PAGE.toInt(), new ColumnFieldLimit(TABLE_ID, 0, 0, 0)), table);
        assertFalse("An unrelated non-TABLE parent is rejected", form(withParent(
                field(TABLE_ID, DataType.CONTROL.toInt(), null))), column, null);
        assertFalse("A mismatched parent TABLE is rejected", form, column,
                field(TABLE_ID + 1, DataType.TABLE.toInt(), new TableFieldLimit()));
        assertFalse("A TABLE parent with no TableFieldLimit is rejected", form,
                column, field(TABLE_ID, DataType.TABLE.toInt(), null));
        assertFalse("An unresolved parent is rejected", form(new HashMap<>()), column, null);
        System.out.println("PASS: " + assertions + " Default naming assertions");
    }

    private static Map<Integer, IFieldObject> withParent(IFieldObject parent) {
        Map<Integer, IFieldObject> result = new HashMap<>();
        result.put(TABLE_ID, parent);
        return result;
    }

    private static IFormObject form(Map<Integer, IFieldObject> byId) {
        return (IFormObject) Proxy.newProxyInstance(
                IFormObject.class.getClassLoader(), new Class<?>[]{IFormObject.class},
                (proxy, method, args) -> "getField".equals(method.getName()) ? byId.get((int)args[0]) : null);
    }

    private static IFieldObject field(int id, int type, FieldLimit limit) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getFieldID": return id;
                case "getDataType": return type;
                case "getFieldLimit": return limit;
                case "toString": return "Field" + id + "/" + type;
                default: return null;
            }
        };
        return (IFieldObject) Proxy.newProxyInstance(
                IFieldObject.class.getClassLoader(), new Class<?>[]{IFieldObject.class}, handler);
    }

    private static boolean eligible(IFormObject form, IFieldObject field, IFieldObject override) throws Exception {
        return (boolean) CHECK.invoke(null, form, field, override);
    }

    private static void assertTrue(String name, IFormObject form, IFieldObject field, IFieldObject override) throws Exception {
        if (!eligible(form, field, override)) throw new AssertionError(name);
        assertions++;
    }

    private static void assertFalse(String name, IFormObject form, IFieldObject field, IFieldObject override) throws Exception {
        if (eligible(form, field, override)) throw new AssertionError(name);
        assertions++;
    }

    private static void assertEquals(String name, int expected, int actual) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        assertions++;
    }
}
