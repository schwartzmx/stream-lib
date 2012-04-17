/*
 * Copyright (C) 2011 Clearspring Technologies, Inc. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clearspring.analytics.stream.cardinality;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class TestHyperLogLog
{
    @Test
    public void testComputeCount()
    {
        HyperLogLog2 hyperLogLog = new HyperLogLog2(.03);
        hyperLogLog.offer(0);
        hyperLogLog.offer(1);
		hyperLogLog.offer(2);
		hyperLogLog.offer(3);
		hyperLogLog.offer(16);
		hyperLogLog.offer(17);
		hyperLogLog.offer(18);
		hyperLogLog.offer(19);
		hyperLogLog.offer(19);
        assertEquals(8, hyperLogLog.cardinality());
    }

    
    @Test
    public void testSerialization() throws IOException
	{
        HyperLogLog2 hll = new HyperLogLog2(.05);
        hll.offer("a");
        hll.offer("b");
		hll.offer("c");
		hll.offer("d");
		hll.offer("e");

		HyperLogLog2 hll2 = HyperLogLog2.Builder.build(hll.getBytes());
        assertEquals(hll.cardinality(), hll2.cardinality());
    }


	@Test
	public void testHighCardinality()
	{
		long start = System.currentTimeMillis();
		HyperLogLog2 hyperLogLog = new HyperLogLog2(.03);
		int size = 1000000000;
		for (int i = 0; i < size; i++)
		{
			hyperLogLog.offer(TestICardinality.streamElement(i));
		}
		System.out.println("time: " + (System.currentTimeMillis() - start));
		long estimate = hyperLogLog.cardinality();
		double err = Math.abs(estimate - size) / (double) size;
		System.out.println(err);
		assertTrue(err < .1);
	}

    
    @Test
    public void testMerge() throws CardinalityMergeException
    {
        int numToMerge = 5;
        double rds = .05;
        int cardinality = 1000000;

		HyperLogLog2[] hyperLogLogs = new HyperLogLog2[numToMerge];

        for(int i=0; i<numToMerge; i++)
        {
            hyperLogLogs[i] = new HyperLogLog2(rds);
            for(int j=0; j<cardinality; j++)
                hyperLogLogs[i].offer(Math.random());
        }

		HyperLogLog2 baseline = new HyperLogLog2(rds);
		for (int j = 0; j < cardinality*numToMerge; j++)
		{
			baseline.offer(Math.random());
		}

		long expectedCardinality = baseline.cardinality();
        HyperLogLog2 hll = hyperLogLogs[0];
        hyperLogLogs = Arrays.asList(hyperLogLogs).subList(1, hyperLogLogs.length).toArray(new HyperLogLog2[0]);
        long mergedEstimate = hll.merge(hyperLogLogs).cardinality();

        double error = Math.abs(mergedEstimate - expectedCardinality) / (double)expectedCardinality;
		System.out.println(mergedEstimate + ":" + error);
        assertTrue(error < .1);
    }
}
