package com.apicontract.provider.serialization;

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
 * 任务16（红-绿）测试：Provider 侧 JSON 序列化实现
 * （与网关侧同语义：编解码往返、失败映射 CONTRACT_INTERNAL_ERROR 不静默）。
 */
class JsonContractSerializerTest {

  private final ContractSerializer serializer = new JsonContractSerializer();

  public static class OrderDto {

    public long orderId;
    public String status;
    public List<String> items;
  }

  @Test
  void shouldExposeJsonName() {
    assertEquals("json", serializer.name());
  }

  @Test
  void shouldRoundTripPrimitiveAndBoxedArguments() {
    Object[] arguments = {"订单", 7, 123456789012345L, false, 2.71};
    Class<?>[] types = {String.class, int.class, long.class, boolean.class,
        double.class};

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(arguments), types);

    assertArrayEquals(arguments, decoded);
  }

  @Test
  void shouldRoundTripNullAndEmptyArguments() {
    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(new Object[] {null}),
        new Class<?>[] {String.class});
    assertNull(decoded[0]);

    assertEquals(0, serializer.decodeArguments(
        serializer.encodeArguments(new Object[0]), new Class<?>[0]).length);
  }

  @Test
  void shouldRoundTripPojoArgument() {
    OrderDto order = new OrderDto();
    order.orderId = 1001L;
    order.status = "PAID";
    order.items = List.of("sku-1", "sku-2");

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(new Object[] {order}),
        new Class<?>[] {OrderDto.class});

    OrderDto restored = (OrderDto) decoded[0];
    assertEquals(1001L, restored.orderId);
    assertEquals("PAID", restored.status);
    assertEquals(List.of("sku-1", "sku-2"), restored.items);
  }

  @Test
  void shouldRoundTripCollectionMapAndBigDecimal() {
    BigDecimal amount = new BigDecimal("9876543210.987654321");

    Object[] decoded = serializer.decodeArguments(
        serializer.encodeArguments(new Object[] {List.of(1, 2), Map.of("k", 1), amount}),
        new Class<?>[] {List.class, Map.class, BigDecimal.class});

    assertEquals(List.of(1, 2), decoded[0]);
    assertEquals(Map.of("k", 1), decoded[1]);
    assertEquals(0, amount.compareTo((BigDecimal) decoded[2]), "大数精度不丢失");
  }

  @Test
  void shouldRoundTripResults() {
    Object decoded = serializer.decodeResult(
        serializer.encodeResult("ok"), String.class);
    assertEquals("ok", decoded);

    assertNull(serializer.decodeResult(
        serializer.encodeResult(null), String.class));
  }

  @Test
  void shouldThrowExplicitlyOnMalformedPayload() {
    SerializationException failure = assertThrows(SerializationException.class,
        () -> serializer.decodeArguments("not-json".getBytes(),
            new Class<?>[] {String.class}));

    assertNotNull(failure.getMessage(), "编解码失败明确抛错，映射 CONTRACT_INTERNAL_ERROR");
    assertNotNull(failure.getCause());
  }

  @Test
  void shouldThrowOnArgumentCountMismatch() {
    byte[] payload = serializer.encodeArguments(new Object[] {"only-one"});

    assertThrows(SerializationException.class,
        () -> serializer.decodeArguments(payload, new Class<?>[] {String.class,
            String.class}));
  }

  @Test
  void shouldThrowOnResultTypeMismatch() {
    assertThrows(SerializationException.class,
        () -> serializer.decodeResult(
            serializer.encodeResult("text"), Long.class));
  }
}
