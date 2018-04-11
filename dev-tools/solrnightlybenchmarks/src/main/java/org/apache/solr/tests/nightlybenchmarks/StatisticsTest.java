package org.apache.solr.tests.nightlybenchmarks;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class StatisticsTest {
	public static void main(String[] args) {
		DescriptiveStatistics stats = new DescriptiveStatistics();
		
		for (int i=0; i<1000000000; i++) {
			stats.addValue(i);
		}
		System.out.println(stats.getPercentile(50));

	}
}
