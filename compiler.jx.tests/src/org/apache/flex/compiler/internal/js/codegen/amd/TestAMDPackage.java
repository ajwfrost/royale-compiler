/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.flex.compiler.internal.js.codegen.amd;

import org.apache.flex.compiler.clients.IBackend;
import org.apache.flex.compiler.internal.as.codegen.TestPackage;
import org.apache.flex.compiler.internal.js.driver.amd.AMDBackend;
import org.apache.flex.compiler.tree.as.IFileNode;
import org.junit.Test;

/**
 * This class tests the production of AMD JavaScript for AS package.
 * 
 * @author Michael Schmalle
 */
public class TestAMDPackage extends TestPackage
{
    /*
     * $0 = defineClass|defineInterface
     * $1 = "./I", "as3/trace", "as3/bind"
     * $2 = I,       trace,       bind_
     * 
     * 
define(["exports", "runtime/AS3", $1],
    function($0,     $2) {
    "use strict";
    
    // constructor 
    function A(arg/~:String~/) {
        A.$$ && A.$$(); // execute static code once on first usage
L#      this.msg = msg;
    }
    
    // private method
    function secret(n) {
L#      return this.msg + n; // add 'this'
    }
    
    return definedClass(A, { implements_: I,
        members: {
        }
        },
        
        staticMembers: {
        },
        
        staticCode: function() {
        
        }
    });
    
  });
     */

    @Override
    @Test
    public void testPackage_Simple()
    {
        IFileNode node = getFileNode("package{}");
        visitor.visitFile(node);
        assertOut("define();");
    }

    @Override
    @Test
    public void testPackage_SimpleName()
    {
        IFileNode node = getFileNode("package foo {}");
        visitor.visitFile(node);
        assertOut("define();");
    }

    @Override
    @Test
    public void testPackage_Name()
    {
        IFileNode node = getFileNode("package foo.bar.baz {}");
        visitor.visitFile(node);
        assertOut("define();");
    }

    @Override
    @Test
    public void testPackageSimple_Class()
    {
        IFileNode node = getFileNode("package {public class A{}}");
        visitor.visitFile(node);
        String code = writer.toString();
        assertOut("define([\"exports\", \"AS3\"], function($exports, AS3) {" +
        		"\n\t\"use strict\"; AS3.class_($exports,\n\tfunction() {" +
        		"\n\t\tvar Super=Object._;\n\t\tvar super$=Super.prototype;\n\t\t" +
        		"return {\n\t\t\tclass_: \"A\",\n\t\t\textends_: Super\n\t\t};\n\t});\n});");
    }

    @Override
    @Test
    public void testPackageQualified_Class()
    {
        IFileNode node = getFileNode("package foo.bar.baz {public class A{}}");
        visitor.visitFile(node);
        //assertOut("");
    }

    @Override
    @Test
    public void testPackageQualified_ClassBody()
    {
        IFileNode node = getFileNode("package foo.bar.baz {public class A{public function A(){}}}");
        visitor.visitFile(node);
        //assertOut("");
    }

    @Override
    @Test
    public void testPackageQualified_ClassBodyMethodContents()
    {
        IFileNode node = getFileNode("package foo.bar.baz {public class A{public function A(){if (a){for (var i:Object in obj){doit();}}}}}");
        visitor.visitFile(node);
        //assertOut("");
    }

    @Override
    protected IBackend createBackend()
    {
        return new AMDBackend();
    }
}
