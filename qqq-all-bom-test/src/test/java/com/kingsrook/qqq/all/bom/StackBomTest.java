/*
 * Copyright 2026 QRunIO, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qqq.all.bom;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import static org.junit.jupiter.api.Assertions.assertEquals;


/*******************************************************************************
 ** Checks qqq-all-bom through this module's effective POM, which Maven writes
 ** with the BOM imports expanded: the BOM manages the whole stack, and this
 ** module depends on every stack artifact the BOM manages, so the build
 ** resolves each one.
 *******************************************************************************/
class StackBomTest
{
   private static final Path EFFECTIVE_POM = Path.of("target", "effective-pom.xml");
   private static final Path BOM_POM       = Path.of("..", "qqq-all-bom", "pom.xml");

   private static List<Coordinate> managed;
   private static List<Coordinate> dependencies;
   private static List<Coordinate> bomDeclared;



   /*******************************************************************************
    ** Read the managed and declared dependencies from the effective POM.
    *******************************************************************************/
   @BeforeAll
   static void beforeAll() throws Exception
   {
      Element effectivePom = parse(EFFECTIVE_POM);
      managed = readDependencies(child(effectivePom, "dependencyManagement"));
      dependencies = readDependencies(effectivePom);

      bomDeclared = readDependencies(child(parse(BOM_POM), "dependencyManagement")).stream()
         .filter(coordinate -> !"import".equals(coordinate.scope()))
         .toList();
   }



   /*******************************************************************************
    ** The BOM manages every artifact the qqq-all stack is built from.
    *******************************************************************************/
   @Test
   void testManagesTheStack()
   {
      Map<String, String> managedVersions = managed.stream().collect(Collectors.toMap(Coordinate::key, Coordinate::version));

      List<String> required = List.of(
         "com.kingsrook.qqq:qqq-backend-core",
         "com.kingsrook.qqq:qqq-esb",
         "com.kingsrook.qqq:qqq-frontend-next",
         "com.kingsrook.qbits:qbit-quick-search",
         "com.kingsrook.qbits:qbit-user-role-permissions",
         "com.kingsrook.qbits:qbit-customizable-table-views",
         "com.kingsrook.qbits:qbit-standard-process-trace",
         "com.kingsrook.qbits:qbit-webhooks",
         "com.kingsrook.qbits:qbit-workflows",
         "com.kingsrook.qbits:qbit-geo-data",
         "com.kingsrook.qbits:qbit-sftp-data-integration",
         "org.postgresql:postgresql",
         "com.mysql:mysql-connector-j",
         "org.xerial:sqlite-jdbc",
         "com.h2database:h2",
         "org.apache.artemis:artemis-jakarta-client",
         "com.rabbitmq.jms:rabbitmq-jms");

      List<String> missing = required.stream().filter(key -> !managedVersions.containsKey(key)).toList();
      assertEquals(List.of(), missing, "Artifacts the stack BOM does not manage");
      assertEquals("3.9.0", managedVersions.get("com.rabbitmq.jms:rabbitmq-jms"));
      assertEquals("1.0.0-RC.8", managedVersions.get("com.kingsrook.qqq:qqq-frontend-next"));
   }



   /*******************************************************************************
    ** This module depends on every stack artifact the BOM manages, so resolving
    ** its test classpath resolves each one from the configured repositories.
    **
    ** Stack artifacts are the managed com.kingsrook artifacts (qqq modules,
    ** qqq-esb, qqq-frontend-next, qBits) plus everything qqq-all-bom declares
    ** itself. The third-party entries qqq-bom-pom inherits from its parent
    ** (Jackson, Netty, Jetty, and others) only align versions, so they are
    ** resolved when something depends on them rather than here.
    *******************************************************************************/
   @Test
   void testDependsOnEveryStackArtifact()
   {
      Set<String> stack = managed.stream()
         .filter(coordinate -> coordinate.groupId().startsWith("com.kingsrook."))
         .map(Coordinate::key)
         .collect(Collectors.toCollection(TreeSet::new));
      bomDeclared.forEach(coordinate -> stack.add(coordinate.key()));

      Set<String> declared = dependencies.stream().map(Coordinate::key).collect(Collectors.toSet());

      List<String> notResolved = stack.stream().filter(key -> !declared.contains(key)).toList();
      assertEquals(List.of(), notResolved, "Stack artifacts missing from qqq-all-bom-test's dependencies, so the build does not resolve them");
   }



   /*******************************************************************************
    ** Parse a POM file and return its project element.
    *******************************************************************************/
   private static Element parse(Path pom) throws Exception
   {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
      return (document.getDocumentElement());
   }



   /*******************************************************************************
    ** Read the dependency elements directly under parent's dependencies element.
    *******************************************************************************/
   private static List<Coordinate> readDependencies(Element parent)
   {
      List<Coordinate> coordinates = new ArrayList<>();
      for(Element dependency : children(child(parent, "dependencies"), "dependency"))
      {
         coordinates.add(new Coordinate(
            text(dependency, "groupId", null),
            text(dependency, "artifactId", null),
            text(dependency, "version", null),
            text(dependency, "type", "jar"),
            text(dependency, "classifier", ""),
            text(dependency, "scope", "compile")));
      }
      return (coordinates);
   }



   /*******************************************************************************
    ** The single child element with the given name.
    *******************************************************************************/
   private static Element child(Element parent, String name)
   {
      List<Element> matches = children(parent, name);
      assertEquals(1, matches.size(), "Expected one <" + name + "> under <" + parent.getTagName() + ">");
      return (matches.get(0));
   }



   /*******************************************************************************
    ** The child elements with the given name (direct children only, so plugin
    ** dependencies are not mistaken for project dependencies).
    *******************************************************************************/
   private static List<Element> children(Element parent, String name)
   {
      List<Element> matches = new ArrayList<>();
      for(Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
      {
         if(node instanceof Element element && element.getTagName().equals(name))
         {
            matches.add(element);
         }
      }
      return (matches);
   }



   /*******************************************************************************
    ** Text of the named child element, or defaultValue when it is absent.
    *******************************************************************************/
   private static String text(Element parent, String name, String defaultValue)
   {
      List<Element> matches = children(parent, name);
      return (matches.isEmpty() ? defaultValue : matches.get(0).getTextContent().trim());
   }



   /*******************************************************************************
    ** A dependency coordinate; key() is how Maven matches a dependency to its
    ** managed entry.
    *******************************************************************************/
   private record Coordinate(String groupId, String artifactId, String version, String type, String classifier, String scope)
   {
      /***************************************************************************
       **
       ***************************************************************************/
      String key()
      {
         String key = groupId + ":" + artifactId;
         if(!type.equals("jar") || !classifier.isEmpty())
         {
            key += ":" + type + (classifier.isEmpty() ? "" : ":" + classifier);
         }
         return (key);
      }
   }

}
