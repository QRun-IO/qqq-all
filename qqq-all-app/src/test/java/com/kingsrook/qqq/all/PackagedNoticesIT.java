/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies this project's notices in the actual shaded application archive. */
class PackagedNoticesIT
{
   @ParameterizedTest
   @ValueSource(strings = {"LICENSE", "NOTICE"})
   void packagedProjectDocumentMatchesRoot(String document) throws Exception
   {
      Path rootDocument = Path.of(System.getProperty("qqqAllRoot"), document);
      try(JarFile jar = new JarFile(System.getProperty("qqqAllJar")))
      {
         String name = "META-INF/qqq-all/" + document;
         var entry = jar.getJarEntry(name);
         assertNotNull(entry, "Missing project document: " + name);
         assertEquals(1L, jar.stream().filter(candidate -> candidate.getName().equals(name)).count(),
            "Project document must occur exactly once: " + name);
         try(var input = jar.getInputStream(entry))
         {
            assertArrayEquals(Files.readAllBytes(rootDocument), input.readAllBytes(),
               "Packaged document must preserve root bytes: " + document);
         }
      }
   }
}
