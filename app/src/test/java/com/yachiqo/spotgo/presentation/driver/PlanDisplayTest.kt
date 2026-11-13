package com.yachiqo.spotgo.presentation.driver

import com.yachiqo.spotgo.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanDisplayTest {
    @Test
    fun backendPlanCodesMapToLocalizedLabelsWithoutExposingInternalIdentifiers() {
        assertEquals(R.string.plans_monthly_name, planDisplayNameResource("DRIVER_MONTHLY"))
        assertEquals(R.string.plans_annual_name, planDisplayNameResource("DRIVER_ANNUAL"))
        assertEquals(R.string.plans_other_name, planDisplayNameResource("FUTURE_INTERNAL_PLAN"))
        assertEquals(R.string.plans_monthly_name, planDisplayNameResource("driver_monthly"))
    }
}
