package mappingrpc.test.json.testcase;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import mappingrpc.test.mock.common.domain.User;

public class GsonTest {
	@Test
	public void test_localDateTime() {
		System.err.println("zonedDateTime:" + ZonedDateTime.now());
		User user = new User();
		user.getFeatures().put("test", 1);
		System.err.println("createTime:" + user.getCreateTime());
		LocalDateTime time = LocalDateTime.parse("2017-10-05T13:35:34.595");
		System.err.println("localDateTime:" + time);

		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {

			@Override
			public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
				return new JsonPrimitive(src.toString());
			}
		});
		gsonBuilder.registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {

			@Override
			public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				return LocalDateTime.parse(json.getAsJsonPrimitive().getAsString());
			}

		});

		Gson gson = gsonBuilder.create();
		String jsonUser = gson.toJson(user);
		System.err.println(jsonUser);

		Gson gson2 = new Gson();
		User user2 = gson.fromJson(jsonUser, User.class);
		System.err.println(user2);
		System.err.println("test:" + user2.getFeatures().getIntValue("test"));
	}
}
