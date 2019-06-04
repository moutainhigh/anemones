package anemones.core.util;

import anemones.core.AnemonesData;
import anemones.core.AnemonesParamConverter;
import com.alibaba.fastjson.JSON;

public class TestConstants {

    public static final AnemonesParamConverter CONVERTER = new AnemonesParamConverter() {
        @Override
        public String serialize(AnemonesData object) {
            return JSON.toJSONString(object);
        }

        @Override
        public AnemonesData deserialize(String param) {
            return JSON.parseObject(param, AnemonesData.class);
        }
    };
}
