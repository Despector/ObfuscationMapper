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
package org.spongepowered.obfuscation;

import org.spongepowered.despector.ast.SourceSet;
import org.spongepowered.despector.config.LibraryConfiguration;
import org.spongepowered.despector.decompiler.Decompiler;
import org.spongepowered.despector.decompiler.Decompilers;
import org.spongepowered.despector.decompiler.JarWalker;
import org.spongepowered.obfuscation.config.ObfConfigManager;
import org.spongepowered.obfuscation.data.MappingsIO;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.MergeEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ObfuscationMapper {

    private static final Map<String, Consumer<String>> flags = new HashMap<>();

    static {
        flags.put("--config=", (arg) -> {
            String config = arg.substring(9);
            Path config_path = Paths.get(".").resolve(config);
            ObfConfigManager.load(config_path);
        });
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: java -jar ObfuscationMapper.jar old.jar old_mappings_dir new.jar");
            return;
        }

        LibraryConfiguration.quiet = true;

        String old_jar = null;
        String old_mappings_dir = null;
        String new_jar = null;
        String output_mappings = null;
        int o = 0;
        outer: for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                for (String flag : flags.keySet()) {
                    if (args[i].startsWith(flag)) {
                        flags.get(flag).accept(args[i]);
                        continue outer;
                    }
                }
                System.err.println("Unknown flag: " + args[i]);
            } else if (o == 0) {
                old_jar = args[i];
            } else if (o == 1) {
                old_mappings_dir = args[i];
            } else if (o == 2) {
                new_jar = args[i];
            } else if (o == 3) {
                output_mappings = args[i];
            }
            o++;
        }

        if (new_jar == null) {
            System.out.println("Missing some args");
            System.out.println("Usage: java -jar ObfuscationMapper.jar old.jar old_mappings_dir new.jar");
            return;
        }
        if (output_mappings == null) {
            output_mappings = "output.srg";
        }

        Path root = Paths.get("");
        Path old_mappings_root = root.resolve(old_mappings_dir).resolve("joined.srg");

        if (!Files.exists(old_mappings_root)) {
            System.err.println("Unknown mappings: " + old_mappings_root.toAbsolutePath().toString());
            return;
        }

        MappingsSet old_mappings = MappingsIO.load(old_mappings_root);
        MappingsSet new_mappings = new MappingsSet();

        Path old_jar_path = root.resolve(old_jar);
        SourceSet old_sourceset = new SourceSet();
        Path new_jar_path = root.resolve(new_jar);
        SourceSet new_sourceset = new SourceSet();

        Decompiler decompiler = Decompilers.JAVA;
        JarWalker walker = new JarWalker(old_jar_path);
        walker.walk(old_sourceset, decompiler);
        System.out.println("Loaded and decompiled " + old_sourceset.getAllClasses().size() + " classes from the older version");

        JarWalker new_walker = new JarWalker(new_jar_path);
        new_walker.walk(new_sourceset, decompiler);
        System.out.println("Loaded and decompiled " + new_sourceset.getAllClasses().size() + " classes from the newer version");

        MergeEngine engine = new MergeEngine(old_sourceset, old_mappings, new_sourceset, new_mappings);
        engine.merge();

        Path mappings_out = root.resolve(output_mappings);
        MappingsIO.write(mappings_out, new_mappings);

    }

}
