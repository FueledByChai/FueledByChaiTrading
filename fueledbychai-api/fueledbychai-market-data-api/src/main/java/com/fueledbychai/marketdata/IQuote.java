
/**
 * MIT License

Copyright (c) 2015  FueledByChai Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
and associated documentation files (the "Software"), to deal in the Software without restriction, 
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, 
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING 
BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, 
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE 
OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 
*/

package com.fueledbychai.marketdata;

import com.fueledbychai.data.Ticker;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Describes what consists of a quote. Level 1 and Level 2 quotes will implement
 * this interface.
 * 
 * 
 *  
 */
public interface IQuote {

        /**
         * The ticker the quote is for.
         * 
         * @return The ticker this quote is for.
         */
        public Ticker getTicker();

        /**
         * The type of quotes this IQuote object contains, ie BID, ASK, MIDPOINT, etc.
         * 
         * @return The type of quote
         */
        public QuoteType[] getTypes();

        /**
         * Returns true if the quote contains the specified quote type
         * 
         * @param type The type of quote to check for
         * @return true if the quote object contains the type of quote
         */
        public boolean containsType(QuoteType type);

        /**
         * The time of the quote
         * 
         * @return The time of the quote.
         */
        public ZonedDateTime getTimeStamp();

        public BigDecimal getValue(QuoteType type);

        /**
         * Returns true if the quote explicitly clears the specified type.
         *
         * This is distinct from {@link #containsType(QuoteType)}. A missing type
         * means "no change" for delta-style updates, while a cleared type means
         * the venue explicitly removed that field from the current market state.
         *
         * @param type The type of quote to check for an explicit clear
         * @return true if the quote explicitly clears the type
         */
        public default boolean isCleared(QuoteType type) {
                return false;
        }

        /**
         * Returns all quote types that were explicitly cleared by this update.
         *
         * @return the cleared quote types
         */
        public default QuoteType[] getClearedTypes() {
                return new QuoteType[0];
        }

        /**
         * Returns true if this quote carries any value updates or explicit clears.
         *
         * @return true if the quote contains any updates
         */
        public default boolean hasUpdates() {
                return getTypes().length > 0 || getClearedTypes().length > 0;
        }
}
