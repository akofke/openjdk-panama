/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;


/**
 * Denotes an unparameterized instruction to produce a value on top of the stack,
 * such as ILOAD, LDC, SIPUSH, DUP, ICONST, etc.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>

 * @see ILOAD
 * @see ICONST
 * @see LDC
 * @see DUP
 * @see SIPUSH
 * @see GETSTATIC
 */
public interface PushInstruction extends StackProducer {
}
