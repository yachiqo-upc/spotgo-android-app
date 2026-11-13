package com.yachiqo.spotgo.presentation.driver

import com.yachiqo.spotgo.R

/** Maps backend-only plan identifiers to user-facing localized labels. */
internal fun planDisplayNameResource(planCode: String): Int = when (planCode.uppercase()) {
    "DRIVER_MONTHLY" -> R.string.plans_monthly_name
    "DRIVER_ANNUAL" -> R.string.plans_annual_name
    else -> R.string.plans_other_name
}
