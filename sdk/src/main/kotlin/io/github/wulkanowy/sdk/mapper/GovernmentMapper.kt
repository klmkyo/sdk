package io.github.wulkanowy.sdk.mapper

import io.github.wulkanowy.sdk.pojo.GovernmentMember
import io.github.wulkanowy.sdk.pojo.GovernmentUnit
import io.github.wulkanowy.sdk.scrapper.home.GovernmentMember as ScrapperGovernmentMember
import io.github.wulkanowy.sdk.scrapper.home.GovernmentUnit as ScrapperGovernmentUnit

fun List<ScrapperGovernmentUnit>.mapToUnits(): List<GovernmentUnit> {
    return map {
        GovernmentUnit(
            unitName = it.unitName,
            people = it.people.mapToMembers()
        )
    }
}

fun List<ScrapperGovernmentMember>.mapToMembers(): List<GovernmentMember> {
    return map {
        GovernmentMember(
            name = it.name,
            division = it.division,
            position = it.position,
            id = it.id
        )
    }
}
