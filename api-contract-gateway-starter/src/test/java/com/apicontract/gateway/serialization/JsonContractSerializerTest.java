package com.apicontract.gateway.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.serialization.SerializationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务16（红-绿）测试：序列化 SPI 与 JSON 默认实现（spec
 * contract-serialization：各参数类型编解码往返、失败不静默）。
 */
class JsonContractSerializerTest {

  private final ContractSerializer serializer = new JsonContractSerializer();

  /** 嵌套对象测试载体（契约 DTO 形态）。 */
  public static class UserDto {

    public int id;
    public String name;
    public List<String> tags;
    public AddressDto address;
  }

  public static class AddressDto {

    public String city;
    public String street;
  }

  private static UserDto user() {
    UserDto user = new UserDto();
    user.id = 7;
    user.name = "张三";
    user.tags = List.of("vip", "beta");
    AddressDto address = new AddressDto();
    address.city = "杭州";
    address.street = "文一西路";
    user.address = address;
    return user;
  }

  @Test
  void shouldExposeJsonName() {
    assertEquals("json", serializer.name());
  }

  @Test
  void shouldRoundTripPrimitiveAndBoxedArguments() {
    Object[] arguments = {"text", 42, 9999999999L, true, 3.14, Integer.valueOf(-1)};
    Class<?>[] types = {String.class, int.class, long.class, boolean.class,
        double.class, Integer.class};

    byte[] payload = serializer.encodeArguments(arguments);
    Object[] decoded = serializer.decodeArguments(payload, types);

    assertArrayEquals(arguments, decoded);
  }

  @Test
  void shouldRoundTripNullArgument() {
    byte[] payload = serializer.encodeArguments(new Object[] {"keep", null});

    Object[] decoded =
        serializer.decodeArguments(payload, new Class<?>[] {String.class, String.class});

    assertEquals("keep", decoded[0]);
    assertNull(decoded[1], "null 参数往返保持 null");
  }

  @Test
  void shouldRoundTripEmptyArguments() {
    byte[] payload = serializer.encodeArguments(new Object[0]);

    assertEquals(0, serializer.decodeArguments(payload, new Class<?>[0]).length);
  }

  @Test
  void shouldRoundTripNestedPojoArgument() {
    UserDto source = user();

    byte[] payload = serializer.encodeArguments(new Object[] {source});
    Object[] decoded =
        serializer.decodeArguments(payload, new Class<?>[] {UserDto.class});

    UserDto restored = (UserDto) decoded[0];
    assertEquals(7, restored.id);
    assertEquals("张三", restored.name);
    assertEquals(List.of("vip", "beta"), restored.tags);
    assertEquals("杭州", restored.address.city, "嵌套对象完整往返");
  }

  @Test
  void shouldRoundTripCollectionAndMapArguments() {
    Object[] arguments = {List.of(1, 2, 3), Map.of("k", "v")};
    Class<?>[] types = {List.class, Map.class};

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(arguments), types);

    assertEquals(arguments[0], decoded[0]);
    assertEquals(arguments[1], decoded[1]);
  }

  @Test
  void shouldRoundTripBigDecimalWithoutPrecisionLoss() {
    BigDecimal amount = new BigDecimal("1234567890.123456789");

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(new Object[] {amount}),
        new Class<?>[] {BigDecimal.class});

    assertEquals(0, amount.compareTo((BigDecimal) decoded[0]), "大数精度不丢失");
  }

  @Test
  void shouldRoundTripResults() {
    UserDto result = user();
    UserDto decoded = (UserDto) serializer.decodeResult(
        serializer.encodeResult(result), UserDto.class);
    assertEquals("张三", decoded.name);

    assertNull(serializer.decodeResult(
        serializer.encodeResult(null), String.class), "null 返回值往返保持 null");

    List<?> list = (List<?>) serializer.decodeResult(
        serializer.encodeResult(List.of("a", "b")), List.class);
    assertEquals(List.of("a", "b"), list);
  }

  @Test
  void shouldThrowExplicitlyOnMalformedPayloadInsteadOfSilentEmpty() {
    SerializationException failure = assertThrows(SerializationException.class,
        () -> serializer.decodeArguments("{bad json".getBytes(),
            new Class<?>[] {String.class}));

    assertNotNull(failure.getMessage(), "编解码失败返回明确错误信息，禁止静默");
    assertNotNull(failure.getCause());
  }

  @Test
  void shouldThrowOnArgumentCountMismatch() {
    byte[] payload = serializer.encodeArguments(new Object[] {"a", "b"});

    assertThrows(SerializationException.class,
        () -> serializer.decodeArguments(payload, new Class<?>[] {String.class}));
  }

  @Test
  void shouldThrowOnResultTypeMismatch() {
    byte[] payload = serializer.encodeResult("not-a-number");

    assertThrows(SerializationException.class,
        () -> serializer.decodeResult(payload, Integer.class));
  }
}
