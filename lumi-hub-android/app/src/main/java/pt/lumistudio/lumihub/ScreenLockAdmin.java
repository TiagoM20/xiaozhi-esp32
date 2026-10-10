package pt.lumistudio.lumihub;

import android.app.admin.DeviceAdminReceiver;

/**
 * Apenas politica force-lock: o utilizador ativa nas Definicoes Android.
 * Nunca pede limpeza remota, alteracao de PIN ou outras politicas.
 */
public final class ScreenLockAdmin extends DeviceAdminReceiver {}
