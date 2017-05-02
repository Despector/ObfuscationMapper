/*
 * The MIT License (MIT)
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.obfuscation.merge.operation;

import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VoteCollector implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {
        List<MatchEntry> matches = new ArrayList<>(set.getPendingMatches());
        int target_type_count = Math.min(matches.size(), Math.max(20, set.getPendingMatches().size() / 10));
        Collections.sort(matches, (a, b) -> b.getVoteDifference() - a.getVoteDifference());
        for (int i = 0; i < target_type_count; i++) {
            MatchEntry m = matches.get(i);
            if (m.getHighest() == null) {
                continue;
            }
            m.setNewType(m.getHighest());
            set.setAsMatched(m);
            set.incrementChangeCount();
            cleanup(set, m.getOldType(), m.getNewType());
        }

        List<MethodMatchEntry> method_matches = new ArrayList<>(set.getPendingMethodMatches());
        int target_method_count = Math.min(method_matches.size(), Math.max(50, set.getPendingMethodMatches().size() / 10));
        Collections.sort(method_matches, (a, b) -> b.getVoteDifference() - a.getVoteDifference());
        for (int i = 0; i < target_method_count; i++) {
            MethodMatchEntry m = method_matches.get(i);
            if (m.getHighest() == null) {
                continue;
            }
            TypeEntry old_owner = set.getOldSourceSet().get(m.getOldMethod().getOwnerName());
            TypeEntry new_owner = set.getNewSourceSet().get(m.getHighest().getOwnerName());
            MatchEntry owner_match = set.getPendingMatch(old_owner);
            if (owner_match.getNewType() == null) {
                if (set.isTypeMatched(new_owner)) {
                    continue;
                }
                owner_match.setNewType(new_owner);
                set.setAsMatched(owner_match);
                set.incrementChangeCount();
                cleanup(set, old_owner, new_owner);
            }
            m.setNewMethod(m.getHighest());
            set.setAsMatched(m);
            set.incrementChangeCount();
        }

        List<FieldMatchEntry> field_matches = new ArrayList<>(set.getPendingFieldMatches());
        int target_field_count = Math.min(field_matches.size(), Math.max(20, set.getPendingFieldMatches().size() / 10));
        Collections.sort(field_matches, (a, b) -> b.getVoteDifference() - a.getVoteDifference());
        for (int i = 0; i < target_field_count; i++) {
            FieldMatchEntry m = field_matches.get(i);
            if (m.getHighest() == null) {
                continue;
            }
            TypeEntry old_owner = set.getOldSourceSet().get(m.getOldField().getOwnerName());
            TypeEntry new_owner = set.getNewSourceSet().get(m.getHighest().getOwnerName());
            MatchEntry owner_match = set.getPendingMatch(old_owner);
            if (owner_match.getNewType() == null) {
                if (set.isTypeMatched(new_owner)) {
                    continue;
                }
                owner_match.setNewType(new_owner);
                set.setAsMatched(owner_match);
                set.incrementChangeCount();
                cleanup(set, old_owner, new_owner);
            }
            m.setNewField(m.getHighest());
            set.setAsMatched(m);
            set.incrementChangeCount();
        }
    }

    private void cleanup(MergeEngine set, TypeEntry type, TypeEntry new_type) {
        for (MethodEntry mth : type.getMethods()) {
            MethodMatchEntry mth_match = set.getPendingMethodMatch(mth);
            if (mth_match.getNewMethod() == null) {
                List<MethodEntry> to_remove = new ArrayList<>();
                for (Map.Entry<MethodEntry, Integer> vote : mth_match.getVotes().entrySet()) {
                    if (!vote.getKey().getOwnerName().equals(new_type.getName())) {
                        to_remove.add(vote.getKey());
                    }
                }
                for (MethodEntry m : to_remove) {
                    mth_match.removeVote(m);
                }
            }
            mth_match.setOwnerMatch(new_type);
        }
        for (MethodEntry mth : type.getStaticMethods()) {
            MethodMatchEntry mth_match = set.getPendingMethodMatch(mth);
            if (mth_match.getNewMethod() == null) {
                List<MethodEntry> to_remove = new ArrayList<>();
                for (Map.Entry<MethodEntry, Integer> vote : mth_match.getVotes().entrySet()) {
                    if (!vote.getKey().getOwnerName().equals(new_type.getName())) {
                        to_remove.add(vote.getKey());
                    }
                }
                for (MethodEntry m : to_remove) {
                    mth_match.removeVote(m);
                }
            }
            mth_match.setOwnerMatch(new_type);
        }
        for (FieldEntry fld : type.getFields()) {
            FieldMatchEntry fld_match = set.getPendingFieldMatch(fld);
            if (fld_match.getNewField() == null) {
                List<FieldEntry> to_remove = new ArrayList<>();
                for (Map.Entry<FieldEntry, Integer> vote : fld_match.getVotes().entrySet()) {
                    if (!vote.getKey().getOwnerName().equals(new_type.getName())) {
                        to_remove.add(vote.getKey());
                    }
                }
                for (FieldEntry m : to_remove) {
                    fld_match.removeVote(m);
                }
            }
            fld_match.setOwnerMatch(new_type);
        }
        for (FieldEntry fld : type.getStaticFields()) {
            FieldMatchEntry fld_match = set.getPendingFieldMatch(fld);
            if (fld_match.getNewField() == null) {
                List<FieldEntry> to_remove = new ArrayList<>();
                for (Map.Entry<FieldEntry, Integer> vote : fld_match.getVotes().entrySet()) {
                    if (!vote.getKey().getOwnerName().equals(new_type.getName())) {
                        to_remove.add(vote.getKey());
                    }
                }
                for (FieldEntry m : to_remove) {
                    fld_match.removeVote(m);
                }
            }
            fld_match.setOwnerMatch(new_type);
        }
    }

}
