package anemones.core;

/**
 * 参数序列化接口
 *
 * @author hason
 */
public interface AnemonesParamConverter {

    /**
     * 序列化
     *
     * @param object 对象
     * @return 字符串
     */
    String serialize(AnemonesData object);

    /**
     * 反序列化
     *
     * @param param 参数
     * @return 反序列化
     */
    AnemonesData deserialize(String param);
}
