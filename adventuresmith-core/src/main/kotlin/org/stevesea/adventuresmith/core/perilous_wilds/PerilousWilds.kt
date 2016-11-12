/*
 * Copyright (c) 2016 Steve Christensen
 *
 * This file is part of Adventuresmith.
 *
 * Adventuresmith is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Adventuresmith is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adventuresmith.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.stevesea.adventuresmith.core.perilous_wilds

import org.stevesea.adventuresmith.core.*


object PwConstants {
    private val GROUP = getFinalPackageName(this.javaClass)

    // TODO: read this list of generators from resource?

    val DETAILS = "${GROUP}/details"

    val PLACE = "${GROUP}/place_names"
    val REGION = "${GROUP}/region_names"

    val NAMES_Arpad = "${GROUP}/names_arpad"
    val NAMES_Oloru = "${GROUP}/names_oloru"
    val NAMES_Tamanarugan = "${GROUP}/names_tamanarugan"
    val NAMES_Valkoina = "${GROUP}/names_valkoina"

    val TREASURE_ITEM = "${GROUP}/treasure_item"
    val TREASURE_UNGUARDED = "${GROUP}/treasure_unguarded"
    val TREASURE_GUARDED = "${GROUP}/treasure_guarded"
    val TREASURE_GUARDED_1BONUS = "${GROUP}/treasure_guarded_1bonus"
    val TREASURE_GUARDED_2BONUS = "${GROUP}/treasure_guarded_2bonus"

    val NPC = "${GROUP}/npc"
    val NPC_RURAL = "${GROUP}/npc_rural"
    val NPC_URBAN = "${GROUP}/npc_urban"
    val NPC_WILDERNESS = "${GROUP}/npc_wilderness"

    val CREATURE = "${GROUP}/creature"
    val CREATURE_Beast = "${GROUP}/creature_beast"
    val CREATURE_Human = "${GROUP}/creature_human"
    val CREATURE_Humanoid = "${GROUP}/creature_humanoid"
    val CREATURE_Monster = "${GROUP}/creature_monster"

    val DANGER = "${GROUP}/danger"

    val EXPLORE_DUNGEON = "${GROUP}/explore_dungeon"
    val FOLLOWER = "${GROUP}/follower"
    val DUNGEON = "${GROUP}/dungeon"
    val STEADING = "${GROUP}/steading"
    val DISCOVERY = "${GROUP}/discovery"

}
