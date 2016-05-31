package org.exoplatform.services.cms;

import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.test.BasicTestCase;

/**
 * Created by AmeniJ on 5/31/16.
 */
public class UtilsTest extends BasicTestCase {
  public void testEvaluateString() {

    String name1 = Utils.cleanString("test.doc");
    assertEquals(name1, "test.doc");
    String name2 = Utils.cleanString("test_doc");
    assertEquals(name2, "test_doc");
    String name3 = Utils.cleanString("test-doc");
    assertEquals(name3, "test-doc");
    String name4 = Utils.cleanString("test/doc");
    assertEquals(name4, "testdoc");
    String name5 = Utils.cleanString("test:doc");
    assertEquals(name5, "testdoc");
    String name6 = Utils.cleanString("test[doc");
    assertEquals(name6, "testdoc");
    String name7 = Utils.cleanString("test]doc");
    assertEquals(name7, "testdoc");
    String name8 = Utils.cleanString("test*doc");
    assertEquals(name8, "testdoc");
    String name9 = Utils.cleanString("test oc");
    assertEquals(name9, "test-oc");
    String name10 = Utils.cleanString("test'doc");
    assertEquals(name10, "testdoc");
    String name11 = Utils.cleanString("test\"doc");
    assertEquals(name11, "testdoc");
    String name12 = Utils.cleanString("test|doc");
    assertEquals(name12, "testdoc");

  }
}

