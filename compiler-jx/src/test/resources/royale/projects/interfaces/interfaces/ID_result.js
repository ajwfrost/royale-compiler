/**
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
/**
 * interfaces.ID
 *
 * @fileoverview
 *
 * @suppress {checkTypes|accessControls}
 */

goog.provide('interfaces.ID');



/**
 * @interface
 */
interfaces.ID = function() {
};


/**
 * Prevent renaming of class. Needed for reflection.
 */
goog.exportSymbol('interfaces.ID', interfaces.ID);


/**
 * Metadata
 *
 * @type {Object.<string, Array.<Object>>}
 */
interfaces.ID.prototype.ROYALE_CLASS_INFO = { names: [{ name: 'ID', qName: 'interfaces.ID', kind: 'interface' }] };



/**
 * Reflection
 *
 * @return {Object.<string, Function>}
 */
interfaces.ID.prototype.ROYALE_REFLECTION_INFO = function () {
  return {};
};
/**
 * @const
 * @type {number}
 */
interfaces.ID.prototype.ROYALE_COMPILE_FLAGS = 9;
