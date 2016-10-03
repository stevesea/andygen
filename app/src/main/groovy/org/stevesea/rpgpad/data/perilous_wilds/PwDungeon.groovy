/*
 * Copyright (c) 2016 Steve Christensen
 *
 * This file is part of RPG-Pad.
 *
 * RPG-Pad is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RPG-Pad is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RPG-Pad.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.stevesea.rpgpad.data.perilous_wilds

import groovy.transform.CompileStatic
import org.stevesea.rpgpad.data.AbstractGenerator
import org.stevesea.rpgpad.data.RangeMap

import javax.inject.Inject

@CompileStatic
class PwDungeon extends AbstractGenerator{
    PwCreature pwCreature
    PwDetails pwDetails
    @Inject
    PwDungeon(PwCreature pwCreature) {
        super(pwCreature.shuffler)
        this.pwCreature = pwCreature
        this.pwDetails = pwCreature.pwDetails
    }

    static enum dungeon_size {
        Small(2, '1d4',  6, '1d6+2'),
        Medium(2, '1d6', 6, '2d6+4'),
        Large(2, '1d6+1',6, '3d6+6'),
        Huge(2, '1d6+2', 6, '4d6+10');

        int theme
        String themeStr
        int areaLimit
        String areaLimitStr

        dungeon_size(int theme, String themeStr, int areaLimit, String areaLimitStr) {
            this.theme = theme
            this.themeStr = themeStr
            this.areaLimit = areaLimit
            this.areaLimitStr = areaLimitStr
        }

        @Override
        public String toString() {
            return "${name()}: themes ${theme} (${themeStr}) Areas ${areaLimit} (${areaLimitStr})"
        }
    }

    // size, themes, areas* (total common and unique)
    RangeMap sizeMap = new RangeMap()
            .with(1..3, dungeon_size.Small.name())
            .with(4..9, dungeon_size.Medium.name())
            .with(10..11, dungeon_size.Large.name())
            .with(12, dungeon_size.Huge.name())
    RangeMap dungeon_ruination = new RangeMap()
            .with(1, 'arcane disaster')
            .with(2, 'damnation/curse')
            .with(3..4, 'earthquake/fire/flood')
            .with(5..6, 'plague/famine/drought')
            .with(7..8, 'overrun by monsters')
            .with(9..10, 'war/invasion')
            .with(11, 'depleted resources')
            .with(12, 'better prospects elsewhere')
    // who built it, to what end?
    RangeMap foundation_builder = new RangeMap()
            .with(1, 'aliens/precursors')
            .with(2, 'demigod/demon')
            .with(3..4, 'natural (caves, etc.)')
            .with(5, 'religious order/cult')
            .with(6..7, "${ -> pick(pwCreature.humanoid)}")
            .with(8..9, 'dwarves/gnomes')
            .with(10, 'elves')
            .with(11, 'wizard/madman')
            .with(12, 'monarch/warlord')
    RangeMap foundation_function = new RangeMap()
            .with(1, 'source/portal')
            .with(2, 'mine')
            .with(3..4, 'tomb/crypt')
            .with(5, 'prison')
            .with(6..7, 'lair/den/hideout')
            .with(8..9, 'stronghold/sanctuary')
            .with(10, 'shrine/temple/oracle')
            .with(11, 'archive/library')
            .with(12, 'unknown/mystery')
    RangeMap theme_mundane = new RangeMap()
            .with(1, 'rot/decay')
            .with(2, 'torture/agony')
            .with(3, 'madness')
            .with(4, 'all is lost')
            .with(5, 'noble sacrifice')
            .with(6, 'savage fury')
            .with(7, 'survival')
            .with(8, 'criminal activity')
            .with(9, 'secrets/treachery')
            .with(10, 'tricks and traps')
            .with(11, 'invasion/infestation')
            .with(12, 'factions at war')
    RangeMap theme_unusual = new RangeMap()
            .with(1, 'creation/invention')
            .with(2, "${ -> pwDetails.pickElement()}")
            .with(3, 'knowledge/learning')
            .with(4, 'growth/expansion')
            .with(5, 'deepening mystery')
            .with(6, 'transformation/change')
            .with(7, 'chaos and destruction')
            .with(8, 'shadowy forces')
            .with(9, 'forbidden knowledge')
            .with(10, 'poison/disease')
            .with(11, 'corruption/blight')
            .with(12, 'impending disaster')
    RangeMap theme_extraordinary = new RangeMap()
            .with(1, 'scheming evil')
            .with(2, 'divination/scrying')
            .with(3, 'blasphemy')
            .with(4, 'arcane research')
            .with(5, 'occult forces')
            .with(6, 'an ancient curse')
            .with(7, 'mutation')
            .with(8, 'the unquiet dead')
            .with(9, 'bottomless hunger')
            .with(10, 'incredible power')
            .with(11, 'unspeakable horrors')
            .with(12, 'holy war')
    //What’s it all about? Choose or roll according to Dungeon Size.
    RangeMap theme = new RangeMap()
            .with(1..5, "${ -> pick(theme_mundane)}")
            .with(6..9, "${ -> pick(theme_unusual)}")
            .with(10..12, "${ -> pick(theme_extraordinary)}")

    RangeMap discovery_dressing = new RangeMap()
            .with(1, 'junk/debris')
            .with(2, 'tracks/marks')
            .with(3, 'signs of battle')
            .with(4, 'writing/carving')
            .with(5, 'warning')
            .with(6, "dead ${ -> pick(pwCreature.creature_no_tags)}")
            .with(7, 'bones/remains')
            .with(8, 'book/scroll/map')
            .with(9, 'broken door/wall')
            .with(10, 'breeze/wind/smell')
            .with(11, 'lichen/moss/fungus')
            .with(12, "${ -> pwDetails.pickOddity()}")
    RangeMap discovery_feature = new RangeMap()
            .with(1, 'cave-in/collapse')
            .with(2, 'pit/shaft/chasm')
            .with(3, 'pillars/columns')
            .with(4, 'locked door/gate')
            .with(5, 'alcoves/niches')
            .with(6, 'bridge/stairs/ramp')
            .with(7, 'fountain/well/pool')
            .with(8, 'puzzle')
            .with(9, 'altar/dais/platform')
            .with(10, 'statue/idol')
            .with(11, 'magic pool/statue/idol')
            .with(12, 'connection to another dungeon')
    RangeMap discovery_find = new RangeMap()
            .with(1, 'trinkets')
            .with(2, 'tools')
            .with(3, 'weapons/armor')
            .with(4, 'supplies/trade goods')
            .with(5, 'coins/gems/jewelry')
            .with(6, 'poisons/potions')
            .with(7, 'adventurer/captive')
            .with(8, 'magic item')
            .with(9, 'scroll/book')
            .with(10, 'magic weapon/armor')
            .with(11, 'artifact')
            .with(12, "${ -> pickN(discovery_find, 2).join(', ')}")

    //A starting point: extrapolate, embellish, integrate.
    RangeMap discovery = new RangeMap()
            .with(1..3, "${ -> pick(discovery_dressing)}")
            .with(4..9, "${ -> pick(discovery_feature)}")
            .with(10..12, "${ -> pick(discovery_find)}")

    RangeMap danger_trap = new RangeMap()
            .with(1, 'alarm')
            .with(2, 'ensnaring/paralyzing')
            .with(3, 'pit')
            .with(4, 'crushing')
            .with(5, 'piercing/puncturing')
            .with(6, 'chopping/slashing')
            .with(7, 'confusing (maze, etc.)')
            .with(8, 'gas (poison, etc.)')
            .with(9, "${ -> pwDetails.pickElement()}")
            .with(10, 'ambush')
            .with(11, 'magical')
            .with(12, "${ -> pickN(danger_trap, 2).join(', ')}")
    RangeMap danger_creature = new RangeMap()
            .with(1, 'waiting in ambush')
            .with(2, 'fighting/squabbling')
            .with(3, 'prowling/on patrol')
            .with(4, 'looking for food')
            .with(5, 'eating/resting')
            .with(6, 'guarding')
            .with(7, 'on the move')
            .with(8, 'searching/scavenging')
            .with(9, 'returning to den')
            .with(10, 'making plans')
            .with(11, 'sleeping')
            .with(12, 'dying')
    RangeMap danger_entity = new RangeMap()
            .with(1, 'alien interloper')
            .with(2, 'vermin lord')
            .with(3, 'criminal mastermind')
            .with(4, 'warlord')
            .with(5, 'high priest')
            .with(6, 'oracle')
            .with(7, 'wizard/witch/alchemist')
            .with(8, "${ -> pick(pwCreature.monster)} lord")
            .with(9, 'evil spirit/ghost')
            .with(10, 'undead lord (lich, etc.)')
            .with(11, 'demon')
            .with(12, 'dark god')
    // if they would notice, show signs of an approaching threat
    RangeMap danger = new RangeMap()
            .with(1..4, "${ -> pick(danger_trap)}")
            .with(5..11, """\
${ -> pick(pwCreature.creature_no_tags)}
<br/>${ -> pick(danger_creature)}
<br/>&nbsp;&nbsp;${ss('Alignment:')} ${ -> pwDetails.pickAlignment()}
<br/>&nbsp;&nbsp;${ss('Disposition:')} ${ -> pwDetails.pickDisposition()}
<br/>&nbsp;&nbsp;${ss('No. Appearing:')} ${ -> pwDetails.pickNumberAppearing()}
""")
            .with(12, "${ -> pick(danger_entity)}")

    @Override
    String generate() {
        String dsizeStr = (String)pick('1d12', sizeMap)
        def dsize = dungeon_size.valueOf(dsizeStr)
        int numThemes = roll(dsize.themeStr)
        int numAreas = roll(dsize.areaLimitStr)
        def countdowns = []
        numThemes.times{
            countdowns.add('&#x25A2')
        }
        def countdownsStr = countdowns.join('&nbsp;')
        return """\
${strong('Dungeon')}
<br/>
<br/>${ss('Size:')} ${dsizeStr} &nbsp;&nbsp;&nbsp;&nbsp;${ss('Area Limit:')} ${numAreas}
<br/>${ss('Builder:')} ${ -> pick(foundation_builder)}
<br/>${ss('Function:')} ${ -> pick(foundation_function)}
<br/>
<br/>${ss('Ruination:')} ${ -> pick(dungeon_ruination)}
<br/>
<br/>${strong('Themes:')}
<br/>&nbsp;&nbsp;${ -> pickN(theme, numThemes).collect{ it.toString() + '&nbsp;' + countdownsStr}.join("<br/>&nbsp;&nbsp;")}\
"""
    }
}
