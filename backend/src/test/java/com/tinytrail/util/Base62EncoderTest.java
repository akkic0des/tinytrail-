package com.tinytrail.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Base62EncoderTest {

    @Test
    void encodesKnownValues() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
        assertThat(Base62Encoder.encode(61)).isEqualTo("Z");   // last digit of the alphabet
        assertThat(Base62Encoder.encode(62)).isEqualTo("10");  // first carry
        assertThat(Base62Encoder.encode(125)).isEqualTo("21");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 63, 3843, 3844, 1_000_000, 123_456_789_012L, Long.MAX_VALUE})
    void roundTrips(long id) {
        assertThat(Base62Encoder.decode(Base62Encoder.encode(id))).isEqualTo(id);
    }

    @Test
    void codesAreDistinctAndCaseSensitive() {
        assertThat(Base62Encoder.encode(10)).isEqualTo("a");
        assertThat(Base62Encoder.encode(36)).isEqualTo("A");
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62Encoder.decode("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62Encoder.decode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62Encoder.decode("ab-c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeOverflowFails() {
        assertThatThrownBy(() -> Base62Encoder.decode("ZZZZZZZZZZZZ")).isInstanceOf(ArithmeticException.class);
    }
}
