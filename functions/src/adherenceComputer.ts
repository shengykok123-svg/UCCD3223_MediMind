import * as admin from "firebase-admin";

const db = admin.firestore;

/** Timezone offset for Asia/Kuala_Lumpur (UTC+8) */
const UTC_PLUS_8_MS = 8 * 60 * 60 * 1000;

/** Get current date string in Asia/Kuala_Lumpur timezone */
export function getTodayMYT(): string {
  const now = new Date(Date.now() + UTC_PLUS_8_MS);
  return now.toISOString().slice(0, 10);
}

/** Get yesterday's date string in Asia/Kuala_Lumpur timezone */
export function getYesterdayMYT(): string {
  const now = new Date(Date.now() + UTC_PLUS_8_MS);
  now.setUTCDate(now.getUTCDate() - 1);
  return now.toISOString().slice(0, 10);
}

/** Get current time string "HH:mm" in Asia/Kuala_Lumpur timezone */
function getCurrentTimeMYT(): string {
  const now = new Date(Date.now() + UTC_PLUS_8_MS);
  const hh = String(now.getUTCHours()).padStart(2, "0");
  const mm = String(now.getUTCMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

/**
 * Parse interval days from frequency string "X/Y"
 * e.g. "1/3" => 3 (every 3 days), "2/1" => 1 (daily)
 */
function getIntervalDays(frequency: string | undefined): number {
  if (!frequency) return 1;
  const parts = frequency.split("/");
  if (parts.length === 2) {
    const val = parseInt(parts[1], 10);
    return isNaN(val) || val <= 0 ? 1 : val;
  }
  return 1;
}

/**
 * Calculate days between two date strings (yyyy-MM-dd)
 */
function daysBetween(dateA: string, dateB: string): number {
  const a = new Date(dateA + "T00:00:00Z");
  const b = new Date(dateB + "T00:00:00Z");
  return Math.floor((b.getTime() - a.getTime()) / (24 * 60 * 60 * 1000));
}

/**
 * Replicate MedicationScheduleItem.isScheduledForDate() logic
 * Checks if a medication with given frequency and startDate is scheduled for targetDate
 */
function isScheduledForDate(
  startDate: string,
  frequency: string | undefined,
  targetDate: string
): boolean {
  const interval = getIntervalDays(frequency);
  if (interval <= 1) return true;
  const diff = daysBetween(startDate, targetDate);
  if (diff < 0) return false;
  return diff % interval === 0;
}

/**
 * Check if today's ISO weekday (1=Monday, 7=Sunday) is in the daysOfWeek string
 * daysOfWeek format: "1,2,3,4,5,6,7"
 */
function isDayOfWeekMatch(
  daysOfWeek: string | undefined,
  targetDate: string
): boolean {
  if (!daysOfWeek || daysOfWeek.trim() === "") return true;
  const d = new Date(targetDate + "T00:00:00Z");
  // JS getUTCDay(): 0=Sunday, 1=Monday, ..., 6=Saturday
  // ISO: 1=Monday, ..., 7=Sunday
  const jsDay = d.getUTCDay();
  const isoDay = jsDay === 0 ? 7 : jsDay;
  const allowed = daysOfWeek.split(",").map((s) => parseInt(s.trim(), 10));
  return allowed.includes(isoDay);
}

/**
 * Check if medication is within its date range for the target date
 */
function isInDateRange(
  startDate: string | undefined,
  endDate: string | undefined,
  targetDate: string
): boolean {
  if (startDate && targetDate < startDate) return false;
  if (endDate && endDate.trim() !== "" && targetDate > endDate) return false;
  return true;
}

/** A single medication+schedule dose entry */
export interface DoseEntry {
  medicationName: string;
  dosage: string;
  scheduledTime: string;
  instructions: string;
  medicationFirestoreId: string;
  scheduleFirestoreId: string;
  status: "TAKEN" | "MISSED" | "PENDING";
  takenTime: string | null;
  finalized: boolean;
  lastUpdatedAt: admin.firestore.Timestamp;
  updatedBy: string;
  // Backwards compatibility fields
  name: string;
  time: string;
}

/** Result of computing adherence for a single user on a date */
export interface AdherenceResult {
  date: string;
  totalScheduled: number;
  takenCount: number;
  missedCount: number;
  pendingCount: number;
  adherenceRate: number;
  finalized: boolean;
  lastComputedAt: admin.firestore.Timestamp;
  computedBy: string;
  medications: DoseEntry[];
}

/**
 * Core computation: compute daily adherence for a single user on a given date
 */
export async function computeUserAdherence(
  firestore: admin.firestore.Firestore,
  uid: string,
  date: string,
  finalize: boolean = false
): Promise<AdherenceResult | null> {
  const userRef = firestore.collection("users").doc(uid);

  // Fetch active, non-deleted medications
  const medsSnap = await userRef.collection("medications").get();
  const activeMeds: admin.firestore.DocumentData[] = [];
  const medMap = new Map<string, admin.firestore.DocumentData>();

  for (const doc of medsSnap.docs) {
    const med = doc.data();
    if (med.isDeleted === true) continue;
    if (!isInDateRange(med.startDate, med.endDate, date)) continue;
    activeMeds.push({...med, _fsId: doc.id});
    medMap.set(doc.id, {...med, _fsId: doc.id});
  }

  if (activeMeds.length === 0) return null;

  // Fetch active schedules
  const schSnap = await userRef.collection("schedules")
    .where("isActive", "==", true)
    .get();

  // Build dose entries for each (medication, schedule) pair valid for today
  const doses: DoseEntry[] = [];
  const currentTime = getCurrentTimeMYT();

  for (const schDoc of schSnap.docs) {
    const sch = schDoc.data();
    const medFsId = sch.medicationFirestoreId;
    if (!medFsId || !medMap.has(medFsId)) continue;

    const med = medMap.get(medFsId)!;

    // Check daysOfWeek
    if (!isDayOfWeekMatch(sch.daysOfWeek, date)) continue;

    // Check frequency interval
    if (!isScheduledForDate(med.startDate, med.frequency, date)) continue;

    // Check if adherence_log exists for this dose
    const logDocId = `${medFsId}_${schDoc.id}_${date}`;
    const logRef = userRef.collection("adherence_logs").doc(logDocId);
    const logSnap = await logRef.get();

    let status: "TAKEN" | "MISSED" | "PENDING" = "PENDING";
    let takenTime: string | null = null;

    if (logSnap.exists) {
      const logData = logSnap.data()!;
      if (logData.status === "TAKEN") {
        status = "TAKEN";
        takenTime = logData.takenDateTime || null;
      } else if (logData.status === "MISSED") {
        status = "MISSED";
      } else if (logData.status === "SKIPPED") {
        // Count skipped as MISSED for adherence purposes
        status = "MISSED";
      }
    } else {
      // No log exists — check if past scheduled time
      const scheduledTime = sch.time || "23:59";
      if (finalize || currentTime > scheduledTime) {
        status = "MISSED";
      }
    }

    const now = db.Timestamp.now();
    doses.push({
      medicationName: med.name || "",
      dosage: med.dosage || "",
      scheduledTime: sch.time || "",
      instructions: sch.instructions || "",
      medicationFirestoreId: medFsId,
      scheduleFirestoreId: schDoc.id,
      status,
      takenTime,
      finalized: finalize,
      lastUpdatedAt: now,
      updatedBy: "cloud_function",
      // Backwards compatibility
      name: med.name || "",
      time: sch.time || "",
    });
  }

  if (doses.length === 0) return null;

  const takenCount = doses.filter((d) => d.status === "TAKEN").length;
  const missedCount = doses.filter((d) => d.status === "MISSED").length;
  const pendingCount = doses.filter((d) => d.status === "PENDING").length;
  const totalScheduled = doses.length;
  const adherenceRate = totalScheduled > 0
    ? Math.round((takenCount / totalScheduled) * 100) / 100
    : 0;

  return {
    date,
    totalScheduled,
    takenCount,
    missedCount,
    pendingCount,
    adherenceRate,
    finalized: finalize,
    lastComputedAt: db.Timestamp.now(),
    computedBy: "cloud_function",
    medications: doses,
  };
}

/**
 * Write the computed adherence result to Firestore
 */
export async function writeAdherenceResult(
  firestore: admin.firestore.Firestore,
  uid: string,
  result: AdherenceResult
): Promise<void> {
  await firestore
    .collection("users")
    .doc(uid)
    .collection("daily_adherence")
    .doc(result.date)
    .set(result);
}

/**
 * Delete a computed daily adherence document when no doses apply anymore.
 * This prevents stale Community data after same-day medication/schedule edits.
 */
export async function deleteAdherenceResult(
  firestore: admin.firestore.Firestore,
  uid: string,
  date: string
): Promise<void> {
  await firestore
    .collection("users")
    .doc(uid)
    .collection("daily_adherence")
    .doc(date)
    .delete();
}

/**
 * Get all user IDs from the users collection
 */
export async function getAllUserIds(
  firestore: admin.firestore.Firestore
): Promise<string[]> {
  const usersSnap = await firestore.collection("users").listDocuments();
  return usersSnap.map((doc) => doc.id);
}
