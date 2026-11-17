package dev.rouzbeh.hybridvpn.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnProfileDao {
    // گرفتن لیست تمام سرورهایی که کاربر ذخیره کرده
    @Query("SELECT * FROM vpn_profiles")
    fun getAllProfiles(): Flow<List<VpnProfile>>

    // گرفتن اون سروری که کاربر برای وصل شدن تیک زده (انتخاب کرده)
    @Query("SELECT * FROM vpn_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<VpnProfile?>

    // ذخیره یک سرور جدید
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VpnProfile)

    // آپدیت کردن اطلاعات یک سرور
    @Update
    suspend fun updateProfile(profile: VpnProfile)

    // پاک کردن یک سرور
    @Delete
    suspend fun deleteProfile(profile: VpnProfile)

    // این دو تابع برای وقتیه که کاربر یه سرور جدید رو برای اتصال انتخاب می‌کنه
    @Query("UPDATE vpn_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE vpn_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActiveProfile(id: Int)

    @Query("SELECT * FROM vpn_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Int): VpnProfile?
}