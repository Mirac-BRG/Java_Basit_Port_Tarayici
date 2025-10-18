# Basit Port Tarayıcı (Java)

Basit bir TCP port tarayıcı. Belirli bir IP/hostname için port aralığını tarar, açık olanları ve bilinen servis adlarını listeler. Sonuçları zaman damgalı `.txt` dosyasına kaydeder.

> ⚠️ **Yalnızca izinli hedefleri tarayın.** İzinsiz ağ taraması hukuka aykırı olabilir.

## Özellikleri:
- TCP connect() tabanlı tarama
- Port aralığı ve timeout ayarı
- Basit servis isimlendirme (well-known ports)
- Metin dosyasına sonuç kaydı

## Kurulum
```bash
javac PortScanner.java
