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
import org.spongepowered.despector.decompiler.DirectoryWalker;
import org.spongepowered.despector.util.TypeHelper;
import org.spongepowered.despector.util.serialization.AstLoader;
import org.spongepowered.despector.util.serialization.MessagePacker;
import org.spongepowered.obfuscation.config.ObfConfigManager;
import org.spongepowered.obfuscation.data.MappingUsageFinder;
import org.spongepowered.obfuscation.data.MappingsIO;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.data.MappingsSet.MethodMapping;
import org.spongepowered.obfuscation.data.UnknownMemberMapper;
import org.spongepowered.obfuscation.data.UnknownTypeMapper;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.operation.CustomMethodMergers;
import org.spongepowered.obfuscation.merge.operation.MatchDiscreteFields;
import org.spongepowered.obfuscation.merge.operation.MatchDiscreteMethods;
import org.spongepowered.obfuscation.merge.operation.MatchEnums;
import org.spongepowered.obfuscation.merge.operation.MatchInnerClasses;
import org.spongepowered.obfuscation.merge.operation.MatchMethodGroups;
import org.spongepowered.obfuscation.merge.operation.MatchReferences;
import org.spongepowered.obfuscation.merge.operation.MatchStringConstants;
import org.spongepowered.obfuscation.merge.operation.MergeInitializers;
import org.spongepowered.obfuscation.merge.operation.MergeMatchedFields;
import org.spongepowered.obfuscation.merge.operation.MergeMatchedMethods;
import org.spongepowered.obfuscation.merge.operation.MergeMatchedTypes;
import org.spongepowered.obfuscation.merge.operation.VoteCollector;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ObfuscationMapper {

    private static final Map<String, Consumer<String>> flags = new HashMap<>();
    private static boolean is_cached = false;
    private static boolean output_unmatched = false;
    private static String validation_mappings = null;

    static {
        flags.put("--config=", (arg) -> {
            String config = arg.substring(9);
            Path config_path = Paths.get(".").resolve(config);
            ObfConfigManager.load(config_path);
        });
        flags.put("--validation=", (arg) -> {
            validation_mappings = arg.substring(13);
        });
        flags.put("--cache", (arg) -> {
            is_cached = true;
        });
        flags.put("--output_unmatched", (arg) -> {
            output_unmatched = true;
        });
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: java -jar ObfuscationMapper.jar old.jar old_mappings.srg new.jar output_mappings.srg");
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
        Path old_mappings_root = root.resolve(old_mappings_dir);

        if (!Files.exists(old_mappings_root)) {
            System.err.println("Unknown mappings: " + old_mappings_root.toAbsolutePath().toString());
            return;
        }

        MappingsSet old_mappings = MappingsIO.load(old_mappings_root);
        MappingsSet new_mappings = new MappingsSet();

        MappingsSet validation = null;
        if (validation_mappings != null) {
            Path validation_mappings_path = root.resolve(validation_mappings);
            if (!Files.exists(validation_mappings_path)) {
                System.err.println("Validation mappings " + validation_mappings + " not found");
            } else {
                System.out.println("Loading validation mappings");
                validation = MappingsIO.load(validation_mappings_path);
            }
        }

        SourceSet old_sourceset = new SourceSet();
        SourceSet new_sourceset = new SourceSet();

        Decompiler decompiler = Decompilers.JAVA;

        Path old_serialized = root.resolve(old_jar.replace('/', '_').replace('\\', '_') + ".ast");
        if (is_cached && Files.exists(old_serialized)) {
            long start = System.nanoTime();
            AstLoader.loadSources(old_sourceset, new BufferedInputStream(new FileInputStream(old_serialized.toFile())));
            long end = System.nanoTime();
            System.out.println("Loaded cached ast with " + old_sourceset.getAllClasses().size() + " classes from the older version");
            System.out.println("Loaded in " + ((end - start) / 1000000) + "ms");
        } else {
            long start = System.nanoTime();
            Path old_jar_path = root.resolve(old_jar);
            DirectoryWalker walker = new DirectoryWalker(old_jar_path);
            walker.walk(old_sourceset, decompiler);
            long end = System.nanoTime();
            System.out.println("Loaded and decompiled " + old_sourceset.getAllClasses().size() + " classes from the older version");
            System.out.println("Loaded in " + ((end - start) / 1000000) + "ms");
            if (is_cached) {
                try (MessagePacker packer = new MessagePacker(new FileOutputStream(old_serialized.toFile()))) {
                    old_sourceset.writeTo(packer);
                }
            }
        }
        Path new_serialized = root.resolve(new_jar.replace('/', '_').replace('\\', '_') + ".ast");
        if (is_cached && Files.exists(new_serialized)) {
            long start = System.nanoTime();
            AstLoader.loadSources(new_sourceset, new BufferedInputStream(new FileInputStream(new_serialized.toFile())));
            long end = System.nanoTime();
            System.out.println("Loaded cached ast with " + new_sourceset.getAllClasses().size() + " classes from the newer version");
            System.out.println("Loaded in " + ((end - start) / 1000000) + "ms");
        } else {
            long start = System.nanoTime();
            Path new_jar_path = root.resolve(new_jar);
            DirectoryWalker walker = new DirectoryWalker(new_jar_path);
            walker.walk(new_sourceset, decompiler);
            long end = System.nanoTime();
            System.out.println("Loaded and decompiled " + new_sourceset.getAllClasses().size() + " classes from the newer version");
            System.out.println("Loaded in " + ((end - start) / 1000000) + "ms");

            if (is_cached) {
                try (MessagePacker packer = new MessagePacker(new FileOutputStream(new_serialized.toFile()))) {
                    new_sourceset.writeTo(packer);
                }
            }
        }

        MergeEngine engine = new MergeEngine(old_sourceset, old_mappings, new_sourceset, new_mappings);

        engine.addOperation(new MatchStringConstants());
        engine.addOperation(new MatchEnums());
        engine.addOperation(new MergeInitializers());
        engine.addOperation(new MatchReferences());
        engine.addOperation(new MatchDiscreteFields());
        engine.addOperation(new MatchMethodGroups());
        engine.addOperation(new MatchDiscreteMethods());
        engine.addOperation(new MatchInnerClasses());
        engine.addOperation(new MergeMatchedTypes());
        engine.addOperation(new CustomMethodMergers());
        engine.addOperation(new MergeMatchedMethods());
        engine.addOperation(new MergeMatchedFields());
        engine.addOperation(new VoteCollector());
        engine.addOperation(MergeEngine.jumpTo(2, (e) -> {
            int ch = e.getChangesLastCycle();
            e.resetChanges();
            return ch > 0;
        }));

        engine.merge();

        MappingUsageFinder usage = new MappingUsageFinder(old_mappings);
        old_sourceset.accept(usage);

        if (validation != null) {
            int type_validation_errors = 0;
            int method_validation_errors = 0;
            int field_validation_errors = 0;

            for (String mapped : new_mappings.getMappedTypes()) {
                String new_mapped = new_mappings.mapTypeSafe(mapped);
                String val_mapped = validation.mapType(mapped);
                if (!new_mapped.equals(val_mapped)) {
                    System.out.println("Mapped " + mapped + " to " + new_mapped + " but should have been " + val_mapped);
                    type_validation_errors++;
                }
            }

            for (String mapped : new_mappings.getMappedMethods()) {
                for (MethodMapping map : new_mappings.getMethods(mapped)) {
                    String new_mapped = new_mappings.mapMethodSafe(map.getObfOwner(), map.getObf(), map.getObfSignature());
                    String val_mapped = validation.mapMethodSafe(map.getObfOwner(), map.getObf(), map.getObfSignature());
                    if (!new_mapped.equals(val_mapped)) {
                        System.out.println("Mapped method " + mapped + " to " + new_mapped + " but should have been " + val_mapped);
                        method_validation_errors++;
                    }
                }
            }

            for (String mapped : new_mappings.getMappedFields()) {
                String new_mapped = new_mappings.mapField(mapped);
                String val_mapped = validation.mapField(mapped);
                if (!new_mapped.equals(val_mapped)) {
                    System.out.println("Mapped field " + mapped + " to " + new_mapped + " but should have been " + val_mapped);
                    field_validation_errors++;
                }
            }

            System.out.println("Mapped " + new_mappings.packagesCount() + " packages");
            float type_percent = (new_mappings.typeCount() / (float) usage.getSeenTypes()) * 100.0f;
            System.out.printf("Mapped %d/%d classes (%.2f%%)\n", new_mappings.typeCount(), usage.getSeenTypes(), type_percent);
            float field_percent = (new_mappings.fieldCount() / (float) usage.getSeenFields()) * 100.0f;
            System.out.printf("Mapped %d/%d fields (%.2f%%)\n", new_mappings.fieldCount(), usage.getSeenFields(), field_percent);
            float method_percent = (new_mappings.methodCount() / (float) usage.getSeenMethods()) * 100.0f;
            System.out.printf("Mapped %d/%d methods (%.2f%%)\n", new_mappings.methodCount(), usage.getSeenMethods(), method_percent);

            System.out.println("Type validation errors: " + type_validation_errors);
            System.out.println("Field validation errors: " + field_validation_errors);
            System.out.println("Method validation errors: " + method_validation_errors);
            int total_mapped = new_mappings.typeCount() + new_mappings.fieldCount() + new_mappings.methodCount();
            int error_count = type_validation_errors + method_validation_errors + field_validation_errors;
            System.out.printf("Accuracy: %.2f%%\n", (1 - (error_count / (float) total_mapped)) * 100);
        } else {
            System.out.println("Mapped " + new_mappings.packagesCount() + " packages");
            float type_percent = (new_mappings.typeCount() / (float) usage.getSeenTypes()) * 100.0f;
            System.out.printf("Mapped %d/%d classes (%.2f%%)\n", new_mappings.typeCount(), usage.getSeenTypes(), type_percent);
            float field_percent = (new_mappings.fieldCount() / (float) usage.getSeenFields()) * 100.0f;
            System.out.printf("Mapped %d/%d fields (%.2f%%)\n", new_mappings.fieldCount(), usage.getSeenFields(), field_percent);
            float method_percent = (new_mappings.methodCount() / (float) usage.getSeenMethods()) * 100.0f;
            System.out.printf("Mapped %d/%d methods (%.2f%%)\n", new_mappings.methodCount(), usage.getSeenMethods(), method_percent);
        }

        if (output_unmatched) {
            List<String> unmatched_types = new ArrayList<>();

            for (String obf : old_mappings.getMappedTypes()) {
                if (!usage.sawType(obf)) {
                    continue;
                }
                String mapped = old_mappings.mapType(obf);
                if (TypeHelper.isAnonClass(mapped)) {
                    continue;
                }
                String new_obf = new_mappings.inverseType(mapped);
                if (new_obf == null) {
                    unmatched_types.add(mapped);
                }
            }

            Collections.sort(unmatched_types);

            Path unmatched_out_path = root.resolve("unmatched.txt");
            System.out.println("Outputting unmatched types to " + unmatched_out_path.toAbsolutePath().toString());
            try (PrintWriter writer = new PrintWriter(unmatched_out_path.toFile())) {
                for (String type : unmatched_types) {
                    writer.println(type);
                }
            }
        }

        UnknownTypeMapper unknown_type = new UnknownTypeMapper(new_mappings);
        new_sourceset.accept(unknown_type);
        UnknownMemberMapper unknown = new UnknownMemberMapper(new_mappings);
        new_sourceset.accept(unknown);

        Path mappings_out = root.resolve(output_mappings);
        MappingsIO.write(mappings_out.toAbsolutePath(), new_mappings);

    }

}
