package com.yachiqo.spotgo.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placeholder client of the future SpotGo backend (Pendientes 3 y 5): while
 * SPOTGO_API_BASE_URL is empty every call reports "not configured" and the
 * app keeps working with the in-memory demo.
 */
class SpotGoApiTest {

    @Test
    fun quoteEscapesTheCharactersThatWouldBreakTheJsonBody() {
        assertEquals("\"plain\"", SpotGoApi.quote("plain"))
        assertEquals("\"\"", SpotGoApi.quote(""))
        assertEquals("\"say \\\"hi\\\"\"", SpotGoApi.quote("say \"hi\""))
        assertEquals("\"back\\\\slash\"", SpotGoApi.quote("back\\slash"))
        assertEquals("\"line\\nbreak\"", SpotGoApi.quote("line\nbreak"))
        assertEquals("\"carriage\\rreturn\"", SpotGoApi.quote("carriage\rreturn"))
        assertEquals("\"tab\\there\"", SpotGoApi.quote("tab\there"))
        // Control characters below 0x20 are escaped as a short unicode code.
        assertEquals("\"bell" + "\\" + "u0007\"", SpotGoApi.quote("bell" + Char(7)))
    }

    @Test
    fun withoutABaseUrlEveryCallReportsTheMissingBackend() {
        if (SpotGoApi.is_configured()) {
            // Someone filled SPOTGO_API_BASE_URL: it must be exposed without a
            // trailing slash so the request paths join cleanly.
            assertFalse(SpotGoApi.base_url.endsWith("/"))
            assertTrue(SpotGoApi.base_url.startsWith("http"))
            return
        }
        assertEquals("", SpotGoApi.base_url)
        assertFalse(SpotGoApi.is_configured())
        assertEquals(ApiResult.NotConfigured, SpotGoApi.post("/api/v1/ping", "{}"))
    }

    @Test
    fun notConfiguredIsAResultAndNotAnException() {
        val result = SpotGoApi.post("/api/v1/anything", "{}")
        assertTrue(result is ApiResult.NotConfigured)
        assertFalse(result is ApiResult.Success)
        assertFalse(result is ApiResult.Failure)
    }
}
