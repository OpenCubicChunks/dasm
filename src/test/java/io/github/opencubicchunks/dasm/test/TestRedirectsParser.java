package io.github.opencubicchunks.dasm.test;

import com.google.gson.JsonElement;
import io.github.opencubicchunks.dasm.RedirectsParser;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet.FieldRedirect;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet.MethodRedirect;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet.TypeRedirect;
import io.github.opencubicchunks.dasm.test.utils.Utils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestRedirectsParser {
    @Test
    public void trivialTest() {
        RedirectsParser redirectsParser = new RedirectsParser();
        JsonElement jsonElement = Utils.parseFileAsJson("trivialTest-set.json");

        List<RedirectSet> redirectSets = assertDoesNotThrow(() -> redirectsParser.parseRedirectSet(jsonElement.getAsJsonObject()));

        assertEquals(1, redirectSets.size());
        RedirectSet set = redirectSets.get(0);
        assertEquals("trivialTest", set.getName());
        assertEquals(0, set.getFieldRedirects().size());
        assertEquals(0, set.getTypeRedirects().size());
        assertEquals(0, set.getMethodRedirects().size());
    }

    @Test
    public void simpleSet() {
        RedirectsParser redirectsParser = new RedirectsParser();
        JsonElement jsonElement = Utils.parseFileAsJson("simpleSet-set.json");

        List<RedirectSet> redirectSets = assertDoesNotThrow(() -> redirectsParser.parseRedirectSet(jsonElement.getAsJsonObject()));

        assertEquals(1, redirectSets.size());
        RedirectSet set = redirectSets.get(0);
        assertEquals("simpleSet", set.getName());
        assertEquals(1, set.getFieldRedirects().size());
        assertEquals(1, set.getTypeRedirects().size());
        assertEquals(1, set.getMethodRedirects().size());

        FieldRedirect fieldRedirect = set.getFieldRedirects().get(0);
        assertTrue(fieldRedirect.field().owner.getClassName().contains("owner"));
        assertTrue(fieldRedirect.field().desc.getClassName().contains("type"));
        assertTrue(fieldRedirect.field().name.contains("fieldRedirectIn"));
        assertEquals("fieldRedirectOut", fieldRedirect.dstFieldName());
        // assertNull(fieldRedirect.field().mappingsOwner()); // TODO: readd if field mappingsOwners are added

        TypeRedirect typeRedirect = set.getTypeRedirects().get(0);
        assertEquals("typeRedirectIn", typeRedirect.srcClassName());
        assertEquals("typeRedirectOut", typeRedirect.dstClassName());

        MethodRedirect methodRedirect = set.getMethodRedirects().get(0);
        assertTrue(methodRedirect.method().owner.getClassName().contains("owner"));
        assertTrue(methodRedirect.method().method.getReturnType().getClassName().contains("type"));
        assertTrue(methodRedirect.method().method.getName().contains("methodRedirectIn"));
        assertEquals("methodRedirectOut", methodRedirect.dstMethodName());
        assertTrue(methodRedirect.method().mappingOwner.getClassName().contains("owner"));
    }
}
