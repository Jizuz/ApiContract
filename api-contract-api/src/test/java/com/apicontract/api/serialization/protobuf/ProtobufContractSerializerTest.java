package com.apicontract.api.serialization.protobuf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.serialization.SerializationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务17（红-绿）测试：Protobuf 序列化实现（DynamicMessage 信封，
 * schemaRef = DTO 类型定位 schema；编解码往返 + 失败不静默）。
 */
class ProtobufContractSerializerTest {

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

  private final ContractSerializer serializer = serializer();

  static ContractSerializer serializer() {
    ContractProtoSchemas schemas = new ContractProtoSchemas();
    schemas.register(AddressDto.class, new ProtoContractSchema("AddressDto", List.of(
        ProtoField.of("city", 1, "string"),
        ProtoField.of("street", 2, "string"))));
    schemas.register(UserDto.class, new ProtoContractSchema("UserDto", List.of(
        ProtoField.of("id", 1, "int"),
        ProtoField.of("name", 2, "string"),
        ProtoField.of("tags", 3, "string").repeated(),
        ProtoField.of("address", 4, "message:AddressDto"))));
    return new ProtobufContractSerializer(schemas);
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
  void shouldExposeProtobufName() {
    assertEquals("protobuf", serializer.name());
  }

  @Test
  void shouldRoundTripScalarArguments() {
    Object[] arguments = {"text", 42, 9999999999L, true, 3.14, Integer.valueOf(-1)};
    Class<?>[] types = {String.class, int.class, long.class, boolean.class,
        double.class, Integer.class};

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(arguments), types);

    assertArrayEquals(arguments, decoded);
  }

  @Test
  void shouldRoundTripNullAndEmptyArguments() {
    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(new Object[] {"keep", null}),
        new Class<?>[] {String.class, String.class});
    assertEquals("keep", decoded[0]);
    assertNull(decoded[1], "null 参数显式表达并往返保持 null");

    assertEquals(0, serializer.decodeArguments(
        serializer.encodeArguments(new Object[0]), new Class<?>[0]).length);
  }

  @Test
  void shouldRoundTripBigDecimalArgument() {
    BigDecimal amount = new BigDecimal("1234567890.123456789");

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(new Object[] {amount}),
        new Class<?>[] {BigDecimal.class});

    assertEquals(0, amount.compareTo((BigDecimal) decoded[0]), "decimal 以字符串承载不丢精度");
  }

  @Test
  void shouldRoundTripNestedPojoArgument() {
    byte[] payload = serializer.encodeArguments(new Object[] {user()});

    Object[] decoded = serializer.decodeArguments(payload,
        new Class<?>[] {UserDto.class});

    UserDto restored = (UserDto) decoded[0];
    assertEquals(7, restored.id);
    assertEquals("张三", restored.name);
    assertEquals(List.of("vip", "beta"), restored.tags);
    assertEquals("杭州", restored.address.city, "嵌套 DTO 完整往返");
  }

  @Test
  void shouldRoundTripResults() {
    UserDto decoded = (UserDto) serializer.decodeResult(
        serializer.encodeResult(user()), UserDto.class);
    assertEquals("张三", decoded.name);

    assertNull(serializer.decodeResult(
        serializer.encodeResult(null), UserDto.class), "null 结果往返保持 null");

    assertEquals("ok", serializer.decodeResult(
        serializer.encodeResult("ok"), String.class));
  }

  @Test
  void shouldFailExplicitlyOnUnsupportedTopLevelCollection() {
    SerializationException failure = assertThrows(SerializationException.class,
        () -> serializer.encodeArguments(new Object[] {List.of(1, 2)}));

    assertTrue(failure.getMessage().contains("集合"),
        "顶层集合参数不支持 protobuf 形态，明确报错不静默: " + failure.getMessage());
  }

  @Test
  void shouldFailOnDtoWithoutRegisteredSchema() {
    assertThrows(SerializationException.class,
        () -> serializer.encodeArguments(new Object[] {new UnregisteredDto()}),
        "未注册 schema 的 DTO 编码失败（SCHEMA_REF_INVALID 语义）");
  }

  @Test
  void shouldFailExplicitlyOnCorruptedPayload() {
    byte[] payload = serializer.encodeArguments(new Object[] {user()});

    SerializationException failure = assertThrows(SerializationException.class,
        () -> serializer.decodeArguments(
            new byte[] {(byte) 0xFF, 0x00, 0x01}, new Class<?>[] {UserDto.class}));

    assertNotNull(failure.getMessage(), "损坏字节流明确抛错，禁止静默空结果");
  }

  public static class UnregisteredDto {

    public String value;
  }
}
