package com.example.storageesp;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(StorageEsp.MODID)
public class StorageEsp {
    public static final String MODID = "storageesp";

    public StorageEsp(FMLJavaModLoadingContext context) {
        // Клиентский код грузим только на клиенте, иначе сервер упадёт
        if (FMLEnvironment.dist.isClient()) {
            StorageEspClient.init(context.getModBusGroup());
        }
    }
}
