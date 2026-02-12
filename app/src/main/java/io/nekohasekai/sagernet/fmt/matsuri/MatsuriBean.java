package io.nekohasekai.sagernet.fmt.matsuri;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import com.google.gson.JsonObject;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.Serializable;
import io.nekohasekai.sagernet.ktx.JsonKt;
import io.nekohasekai.sagernet.ktx.Logs;
import io.nekohasekai.sagernet.plugin.MatsuriPluginManager;
import io.nekohasekai.sagernet.plugin.MatsuriPluginManager.Protocol;
import org.jetbrains.annotations.NotNull;

public class MatsuriBean extends AbstractBean {

    public static final Serializable.CREATOR<MatsuriBean> CREATOR = new Serializable.CREATOR<MatsuriBean>() {
        @NonNull
        @Override
        public MatsuriBean newInstance() {
            return new MatsuriBean();
        }

        @Override
        public MatsuriBean[] newArray(int size) {
            return new MatsuriBean[size];
        }
    };

    public JsonObject allConfig = null;
    public String plgId;
    public String protocolId;
    public JsonObject sharedStorage = new JsonObject();

    @NotNull
    public static JsonObject tryParseJSON(String input) {
        JsonObject ret;
        try {
            ret = JsonKt.parseJson(input, true).getAsJsonObject();
        } catch (Exception e) {
            ret = new JsonObject();
            Logs.INSTANCE.e(e.toString());
        }
        return ret;
    }

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocolId == null) protocolId = "";
        if (plgId == null) plgId = "moe.matsuri.plugin.donotexist";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(plgId);
        output.writeString(protocolId);
        output.writeString(sharedStorage.toString());
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        plgId = input.readString();
        protocolId = input.readString();
        sharedStorage = tryParseJSON(input.readString());
    }

    public String displayType() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return "Unknown";
        return p.getProtocolId();
    }

    @Override
    public boolean canMapping() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return false;
        return p.getProtocolConfig().get("canMapping").getAsBoolean();
    }

    public boolean canICMPing() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return false;
        return p.getProtocolConfig().get("canICMPing").getAsBoolean();
    }

    public boolean canTCPing() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return false;
        return p.getProtocolConfig().get("canTCPing").getAsBoolean();
    }

    @NotNull
    @Override
    public MatsuriBean clone() {
        return KryoConverters.deserialize(new MatsuriBean(), KryoConverters.serialize(this));
    }
}
