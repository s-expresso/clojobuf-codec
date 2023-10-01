#include <fstream>

#include "generated-cpp/reference.pb.h"

#define GEN_BIN(TYPE, VALUE) \
 { \
    Singular sin; \
    sin.set_##TYPE##_val(VALUE); \
    std::ofstream ofs("generated-bin/singular/" #TYPE "_" #VALUE ".protobin"); \
    sin.SerializeToOstream(&ofs); \
 }

#define GEN_ENUM_BIN(VALUE, INT_VALUE) \
 { \
    Singular sin; \
    sin.set_enum_val(VALUE); \
    std::ofstream ofs("generated-bin/singular/enum_" #INT_VALUE ".protobin"); \
    sin.SerializeToOstream(&ofs); \
 }

#define GEN_PACKED_BIN(TYPE, V1, V2, V3, V4, V5) \
{ \
    Packed packed; \
    packed.add_##TYPE##_val(V1); \
    packed.add_##TYPE##_val(V2); \
    packed.add_##TYPE##_val(V3); \
    packed.add_##TYPE##_val(V4); \
    packed.add_##TYPE##_val(V5); \
    std::ofstream ofs("generated-bin/packed/" #TYPE ".protobin"); \
    packed.SerializeToOstream(&ofs); \
}

#define GEN_STR_BIN(TYPE, VALUE, FILE_SUFFIX) \
 { \
    Singular sin; \
    sin.set_##TYPE##_val(VALUE); \
    std::ofstream ofs("generated-bin/singular/" #TYPE "_" #FILE_SUFFIX ".protobin"); \
    sin.SerializeToOstream(&ofs); \
 }

int main() {
    // wire-type 0
    GEN_BIN(int32, 2147483647);
    GEN_BIN(int32, -2147483648);

    GEN_BIN(int64, 9223372036854775807);
    GEN_BIN(int64, -9223372036854775808);

    GEN_BIN(uint32, 4294967295);

    GEN_BIN(uint64, 18446744073709551615);

    GEN_BIN(sint32, 2147483647);
    GEN_BIN(sint32, -2147483648);

    GEN_BIN(sint64, 9223372036854775807);
    GEN_BIN(sint64, -9223372036854775808);

    GEN_BIN(bool, true);
    GEN_BIN(bool, false);

    GEN_ENUM_BIN(THREE, 3);
    GEN_ENUM_BIN(MINUS_ONE, -1);

    // wire-type 1
    GEN_BIN(fixed64, 18446744073709551615);

    GEN_BIN(sfixed64, 9223372036854775807);
    GEN_BIN(sfixed64, -9223372036854775808);

    GEN_BIN(double, 123.456);
    GEN_BIN(double, -123.456);

    // wire-type 2
    GEN_STR_BIN(string, "the quick brown fox", the_quick_brown_fox);
    GEN_STR_BIN(string, "一二三四五", one_two_three_four_five);

    // wire-type 5
    GEN_BIN(fixed32, 4294967295);

    GEN_BIN(sfixed32, 2147483647);
    GEN_BIN(sfixed32, -2147483648);

    GEN_BIN(float, 1.23);
    GEN_BIN(float, -1.23);

    // packed: wire-type 2
    GEN_PACKED_BIN(int32, 0, 12345, -12345, 2147483647, -2147483648);
    GEN_PACKED_BIN(int64, 0, 12345, -12345, 9223372036854775807, -9223372036854775808);
    GEN_PACKED_BIN(uint32, 0, 12345, 23456, 34567, 4294967295);
    GEN_PACKED_BIN(uint64, 0, 12345, 23456, 34567, 18446744073709551615);
    GEN_PACKED_BIN(sint32, 0, 12345, -12345, 2147483647, -2147483648);
    GEN_PACKED_BIN(sint64, 0, 12345, -12345, 9223372036854775807, -9223372036854775808);
    GEN_PACKED_BIN(bool, true, false, true, false, true);
    GEN_PACKED_BIN(enum, MINUS_ONE, ZERO, ONE, TWO, THREE);
    GEN_PACKED_BIN(fixed64, 0, 12345, 23456, 34567, 18446744073709551615);
    GEN_PACKED_BIN(sfixed64, 0, 12345, -12345, 9223372036854775807, -9223372036854775808);
    GEN_PACKED_BIN(double, 0, 123.45, -123.45, 12345.678, -12345.678);
    GEN_PACKED_BIN(fixed32, 0, 12345, 23456, 34567, 4294967295);
    GEN_PACKED_BIN(sfixed32, 0, 12345, -12345, 2147483647, -2147483648);
    GEN_PACKED_BIN(float, 0, 12.0, -12.0, 123.0, -123.0);
}
