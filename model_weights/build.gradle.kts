plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName = "model_weights"
    dynamicDelivery {
        deliveryType = "install-time"
    }
}

