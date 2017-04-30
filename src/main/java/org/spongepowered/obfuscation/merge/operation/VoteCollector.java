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

import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        }

        List<MethodMatchEntry> method_matches = new ArrayList<>(set.getPendingMethodMatches());
        int target_method_count = Math.min(method_matches.size(), Math.max(50, set.getPendingMethodMatches().size() / 10));
        Collections.sort(method_matches, (a, b) -> b.getVoteDifference() - a.getVoteDifference());
        for (int i = 0; i < target_method_count; i++) {
            MethodMatchEntry m = method_matches.get(i);
            if (m.getHighest() == null) {
                continue;
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
            m.setNewField(m.getHighest());
            set.setAsMatched(m);
            set.incrementChangeCount();
        }

    }

}
