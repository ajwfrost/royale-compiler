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

package org.apache.royale.compiler.problems;

/**
 * An unsigned digest element from the catalog.xml of a SWC is missing.
 * An unsigned digest is required to use an unsigned RSL (SWF).
 */
public final class MissingUnsignedDigestProblem extends CompilerProblem
{
    public static final String DESCRIPTION =
        "No unsigned digest found in catalog.xml of the library, ${libraryPath}.";

    public static final int errorCode = 1482;
    public MissingUnsignedDigestProblem(String libraryPath)
    {
        super();
        this.libraryPath = libraryPath;
    }

    public final String libraryPath;
}
