/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.search;

import io.druid.query.search.search.SearchHit;
import io.druid.query.search.search.SearchSortSpec;
import io.druid.query.search.search.StrlenSearchSortSpec;
import org.junit.Assert;
import org.junit.Test;

/**
 */
public class StrlenSearchSortSpecTest
{
  @Test
  public void testComparator()
  {
    SearchSortSpec spec = new StrlenSearchSortSpec();

    SearchHit hit1 = new SearchHit("test", "a");
    SearchHit hit2 = new SearchHit("test", "apple");
    SearchHit hit3 = new SearchHit("test", "elppa");

    Assert.assertTrue(spec.getComparator().compare(hit2, hit3) < 0);
    Assert.assertTrue(spec.getComparator().compare(hit2, hit1) > 0);
    Assert.assertTrue(spec.getComparator().compare(hit1, hit3) < 0);
  }
}
