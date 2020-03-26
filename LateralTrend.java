import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Collections;
import java.util.Date;
import java.util.List;


/**
 * Program that finds the longest lateral trend in price data. The option
 * {@code --max-pct-change} controls how far apart the high and low (closing)
 * prices can be, in percentage terms, during a period for it to be consider a
 * lateral trend. This defaults to 5%.
 */
public class LateralTrend {

	/** Entry point for a program to build a model of NFL teams. */
	public static void main(String[] args) throws Exception {
		ArgParser argParser = new ArgParser("LateralTrend");
		argParser.addOption("max-pct-change", Double.class);
		argParser.addOption("brute", Boolean.class);
		args = argParser.parseArgs(args, 1, 1);

		double maxPctChange = argParser.hasOption("max-pct-change") ?
				argParser.getDoubleOption("max-pct-change") : 5.0;
				
				List<Integer> prices = loadPrices(args[0]);

				Range longest;
				long start = System.currentTimeMillis();
				if (argParser.hasOption("brute")) {
					longest = findLongestLateralTrendBrute(maxPctChange, prices);
				} else {
					longest = findLongestLateralTrend(
							maxPctChange, prices, 0, prices.size()-1);
				}
				long stop = System.currentTimeMillis();
				System.out.printf(
						"Longest lateral trend has length %d\n",
						longest.length());
				System.out.printf("Price range is %.2f to %.2f, a %.1f%% change\n",
						longest.lowPrice/100., longest.highPrice/100.,
						100. * (longest.highPrice - longest.lowPrice) / longest.lowPrice);
				System.out.println("Time: " + (stop-start) + " ms");
	}

	/**
	 * Returns the prices in the file. Prices are returned in units of cents
	 * ($0.01) to avoid roundoff issues elsewhere in the code.
	 * @param fileName Name of the CSV file containing price data
	 */
	private static List<Integer> loadPrices(String fileName)
			throws IOException, ParseException {

		List<Integer> prices = new ArrayList<Integer>();

		CsvParser parser = new CsvParser(fileName);
		while (parser.hasNext()) {
			String[] parts = parser.next();
			double close = Double.parseDouble(parts[1]);
			prices.add((int)(100 * close));
		}

		return prices;
	}

	/** Returns the range with the longest lateral trend in the price data. */
	private static Range findLongestLateralTrendBrute(
			double maxPctChange, List<Integer> prices) {
		
		int i = 0;
		int j;
		
		Range longestRange = null;
		while(i <= prices.size()-2) { //up to the second to last index
			Range iRange = Range.fromOneIndex(i, prices);
			j = i+1;				//j starts one index after i
			while(j <= prices.size()-1) { // j goes up to the last index
				//compare indexes
				Range jRange = Range.fromOneIndex(j, prices);
				
				iRange = iRange.concat(jRange);
				if(iRange.percentChangeAtMost(maxPctChange)) { //if true, then we have a lateral trend
					if(longestRange == null || longestRange.length() <= iRange.length()) {	// if the current trend is longer than the previous max
						longestRange = iRange;
					}
				}
				else {					//if its not a lateral trend, move i up one index
					iRange = null;
					i++;
					break;
				}
				j++;
			}
			i++;
		}
		return longestRange;
	}

	/**
	 * Returns the range with the longest lateral trend in the price data from
	 * {@code firstIndex} to {@code lastIndex} (inclusive).
	 */
	private static Range findLongestLateralTrend(double maxPctChange,
			List<Integer> prices, int firstIndex, int lastIndex) {
		assert firstIndex <= lastIndex;
		
		int lowerIndex = prices.size()/2-1;
		int upperIndex = lowerIndex+1;
		Range bestRange = null;
		while(prices.size() > 0) {


			//		
			//		List<Integer> lower = prices.subList(0, prices.size()/2);
			//		Range lowerRange = findLongestLateralTrendCrossingMidpoint(maxPctChange, prices, lastIndex, lastIndex, lastIndex);	//lower
			//		
			//		List<Integer> upper = prices.subList(0, prices.size()/2);
			//		Range upperRange = findLongestLateralTrendCrossingMidpoint(maxPctChange, prices, firstIndex, lastIndex, lastIndex);//lower
			//		
			List<Integer> lower = prices.subList(lowerIndex, prices.size()/2);
			Range lowerRange = findLongestLateralTrendBrute(maxPctChange, lower);	//lower

			List<Integer> upper = prices.subList(prices.size()/2, upperIndex);
			Range upperRange = findLongestLateralTrendBrute(maxPctChange, upper);//upper

			Range bestHalf = lowerRange.length() >= upperRange.length() ? lowerRange : upperRange;
			Range cross = findLongestLateralTrendCrossingMidpoint(maxPctChange, prices, 0, lowerRange.length(), lowerRange.length() + upperRange.length());//crossing

			bestRange = bestHalf.length() >= cross.length() ? bestHalf : cross;
			
			for(int x = prices.size()/2-bestRange.length(); x<prices.size()/2 + bestRange.length(); x++) {
				prices.remove(x);
			}
			lowerIndex-=bestRange.length();
			upperIndex+=bestRange.length();
			
		}
		
		
		return bestRange;
	}

	/**
	 * Returns the range with the longest lateral trend in the price data from
	 * {@code firstIndex} to {@code lastIndex} (inclusive) that starts
	 * at or before {@code m} and ends at or after {@code m+1}. If
	 * no such range defines a lateral trend, then it returns a lateral
	 * trend Range.fromOneIndex(m, prices).
	 **/
	private static Range findLongestLateralTrendCrossingMidpoint(
			double maxPctChange, List<Integer> prices,
			int firstIndex, int midIndex, int lastIndex) {

		assert (firstIndex <= midIndex && midIndex+1 <= lastIndex);
		int firstTryIndex = midIndex;
		Range longestRangeMerge = null;
		while(firstTryIndex >= firstIndex) {
			Range firstHalfMerge = Range.fromOneIndex(firstTryIndex, prices);
			int secondTryIndex = firstTryIndex+1;
			while(secondTryIndex <= lastIndex) {
				Range secondHalfMerge = Range.fromOneIndex(secondTryIndex, prices);
				firstHalfMerge = firstHalfMerge.concat(secondHalfMerge);
				if(firstHalfMerge.percentChangeAtMost(maxPctChange)) {	//if a lateral trend
					if(longestRangeMerge == null || longestRangeMerge.length() <= firstHalfMerge.length() ) {
						longestRangeMerge = firstHalfMerge;
					}
				}
				else {				//if not a lateral trend
					firstHalfMerge = null;
					firstTryIndex--;
					break;
				}
				secondTryIndex++;
			}
			firstTryIndex--;
		}
		
		return longestRangeMerge;
		
		
		
		//step 1: Divide the array of prices into the first and second halves and do brute force search

		// this is brute force function but also with creating a list (first half)
		
//		int i = 0;
//		int j;
//		
//		Range longestRangeFirstHalf = null;
//		//ArrayList<Range> firstHalfRangeList = new ArrayList<Range>();
//		int smallestIndex = -1;
//		boolean firstCrossMid = false;
//		while(i <= (prices.size()/2)-2) { //up to the second to last index
//			Range iRange = Range.fromOneIndex(i, prices);
//			j = i+1;				//j starts one index after i
//			while(j <= (prices.size()/2)-1) { // j goes up to the last index
//				//compare indexes
//				Range jRange = Range.fromOneIndex(j, prices);
//				
//				iRange = iRange.concat(jRange);
//				if(iRange.percentChangeAtMost(maxPctChange)) { //if true, then we have a lateral trend
//					if(j==(prices.size()/2)-1) {
//						//System.out.println("added first half");
////						firstHalfRangeList.add(iRange);
//						if(smallestIndex == -1 || i < smallestIndex) {
//							firstCrossMid = true;
//							smallestIndex = i;
//						}
//					}
//					if(longestRangeFirstHalf == null || longestRangeFirstHalf.length() <= iRange.length()) {	// if the current trend is longer than the previous max
//						longestRangeFirstHalf = iRange;
//					}
//				}
//				else {					//if its not a lateral trend, move i up one index
//					iRange = null;
//					i++;
//					break;
//				}
//				j++;
//			}
//			i++;
//		}
//		//end
//
//		// this is brute force function but also with creating a list (second half)
//		boolean secondCrossMid = false;
//		i = prices.size()/2;
//		Range longestRangeSecondHalf = null;
//		//ArrayList<Range> secondHalfRangeList = new ArrayList<Range>();
//		int largestIndex = -1;
//		while(i <= prices.size()-2) { //up to the second to last index
//			Range iRange = Range.fromOneIndex(i, prices);
//			j = i+1;				//j starts one index after i
//			while(j <= prices.size()-1) { // j goes up to the last index
//				//compare indexes
//				Range jRange = Range.fromOneIndex(j, prices);
//				
//				iRange = iRange.concat(jRange);
//				if(iRange.percentChangeAtMost(maxPctChange)) { //if true, then we have a lateral trend
//					if(i==(prices.size()-2)) {
////						System.out.println("added second half");
//						//secondHalfRangeList.add(iRange);
//						if(largestIndex == -1 || i < largestIndex) {
//							largestIndex = i;
//							secondCrossMid = true;
//						}
//					}
//					if(longestRangeSecondHalf == null || longestRangeSecondHalf.length() <= iRange.length()) {	// if the current trend is longer than the previous max
//						longestRangeSecondHalf = iRange;
//					}
//				}
//				else {					//if its not a lateral trend, move i up one index
//					iRange = null;
//					i++;
//					break;
//				}
//				j++;
//			}
//			i++;
//		}
//		//end
//
//		
//		Range maxHalf = longestRangeFirstHalf.length() < longestRangeSecondHalf.length() ? longestRangeSecondHalf : longestRangeFirstHalf;
//
//		if(firstCrossMid == true && secondCrossMid == true) {
//			Range longestRangeMerge = null;
//
//			int firstTryIndex = smallestIndex;
//			while(firstTryIndex >= 0) {
//				Range firstHalfMerge = Range.fromOneIndex(firstTryIndex, prices);
//				int secondTryIndex = firstTryIndex+1;
//				while(secondTryIndex <= largestIndex) {
//					Range secondHalfMerge = Range.fromOneIndex(secondTryIndex, prices);
//					firstHalfMerge = firstHalfMerge.concat(secondHalfMerge);
//					if(firstHalfMerge.percentChangeAtMost(maxPctChange)) {	//if a lateral trend
//						if(longestRangeMerge == null || longestRangeMerge.length() <= firstHalfMerge.length() ) {
//							longestRangeMerge = firstHalfMerge;
//						}
//					}
//					else {				//if not a lateral trend
//						firstHalfMerge = null;
//						firstTryIndex--;
//						break;
//					}
//					secondTryIndex++;
//				}
//				firstTryIndex--;
//			}
//			
//			Range overallMax = maxHalf.length() > longestRangeMerge.length() ? maxHalf : longestRangeMerge;
//			return overallMax;
//		}
//		else {
//			return maxHalf;
//		}


	}
}
