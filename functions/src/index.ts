import * as admin from "firebase-admin";
import {onSchedule} from "firebase-functions/v2/scheduler";
import {onDocumentWritten} from "firebase-functions/v2/firestore";
import {
  computeUserAdherence,
  writeAdherenceResult,
  getAllUserIds,
  getTodayMYT,
  getYesterdayMYT,
} from "./adherenceComputer";

admin.initializeApp();
const firestore = admin.firestore();

/**
 * 1. computeAdherencePeriodic — Scheduled every 15 minutes
 *
 * Iterates all users and computes today's dose-level adherence status.
 * Writes/updates `daily_adherence/{today}` for each user with active medications.
 * This ensures Community shows data even when the target user hasn't opened the app.
 */
export const computeAdherencePeriodic = onSchedule(
  {
    schedule: "every 15 minutes",
    timeZone: "Asia/Kuala_Lumpur",
    region: "asia-southeast1",
  },
  async () => {
    const today = getTodayMYT();
    const userIds = await getAllUserIds(firestore);

    console.log(
      `computeAdherencePeriodic: Processing ${userIds.length} users for ${today}`
    );

    let processed = 0;
    let skipped = 0;

    for (const uid of userIds) {
      try {
        const result = await computeUserAdherence(firestore, uid, today);
        if (result) {
          await writeAdherenceResult(firestore, uid, result);
          processed++;
        } else {
          skipped++;
        }
      } catch (err) {
        console.error(`Error processing user ${uid}:`, err);
      }
    }

    console.log(
      `computeAdherencePeriodic: Done. Processed=${processed}, Skipped=${skipped}`
    );
  }
);

/**
 * 2. onAdherenceLogWrite — Firestore trigger
 *
 * Fires when a user marks a dose as taken/missed/skipped.
 * Recomputes that single user's `daily_adherence/{today}` immediately.
 * Ensures near-instant update for Community viewers.
 */
export const onAdherenceLogWrite = onDocumentWritten(
  {
    document: "users/{uid}/adherence_logs/{logId}",
    region: "asia-southeast1",
  },
  async (event) => {
    const uid = event.params.uid;
    const today = getTodayMYT();

    console.log(
      `onAdherenceLogWrite: Triggered for user ${uid}, log ${event.params.logId}`
    );

    try {
      const result = await computeUserAdherence(firestore, uid, today);
      if (result) {
        await writeAdherenceResult(firestore, uid, result);
        console.log(`onAdherenceLogWrite: Updated daily_adherence for ${uid}`);
      }
    } catch (err) {
      console.error(`onAdherenceLogWrite: Error for user ${uid}:`, err);
    }
  }
);

/**
 * 3. finalizePreviousDayAtMidnight — Scheduled daily at 00:05 MYT
 *
 * For each user, reads yesterday's `daily_adherence` document.
 * Any dose still PENDING is set to MISSED. Sets `finalized: true` on the
 * document and each medication entry. Recomputes final counts and adherenceRate.
 */
export const finalizePreviousDayAtMidnight = onSchedule(
  {
    schedule: "5 0 * * *",
    timeZone: "Asia/Kuala_Lumpur",
    region: "asia-southeast1",
  },
  async () => {
    const yesterday = getYesterdayMYT();
    const userIds = await getAllUserIds(firestore);

    console.log(
      `finalizePreviousDayAtMidnight: Finalizing ${yesterday} for ${userIds.length} users`
    );

    let finalized = 0;

    for (const uid of userIds) {
      try {
        const result = await computeUserAdherence(
          firestore, uid, yesterday, true
        );
        if (result) {
          await writeAdherenceResult(firestore, uid, result);
          finalized++;
        }
      } catch (err) {
        console.error(`Error finalizing user ${uid}:`, err);
      }
    }

    console.log(
      `finalizePreviousDayAtMidnight: Done. Finalized=${finalized}`
    );
  }
);
