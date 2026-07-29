package com.arshraj.vakilconnect.reference.enums;

/**
 * Where a city alias came from.
 *
 * Worth recording because the three have different trust levels and different
 * lifecycles:
 *
 *   SEED      shipped in the V3 migration - historical renames in daily use
 *   ADMIN     added by an administrator, typically after a "my city is missing"
 *             report, to avoid creating a duplicate city
 *   MIGRATION created while reconciling the existing free-text `lawyers.city`
 *             column, so those mappings can be audited or reverted separately
 */
public enum AliasSource {
    SEED,
    ADMIN,
    MIGRATION
}
