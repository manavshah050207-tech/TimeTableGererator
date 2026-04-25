import java.util.*;
import java.io.*;

/**
 * ============================================================
 * AUTOMATIC TIMETABLE GENERATOR - Java CLI Edition
 * For Engineering Students | Single-file, zero dependencies
 * ============================================================
 *
 * Algorithm : BACKTRACKING (Constraint Satisfaction Problem)
 * Priority-weighted allocation decides HOW MANY times
 * each subject appears; backtracking decides WHERE.
 *
 * Compile : javac TimetableGenerator.java
 * Run : java TimetableGenerator
 *
 * Features :
 * - Subject priorities (Low / Normal / High)
 * - Fixed slots (e.g. "Math always on Monday slot 1")
 * - Break / lunch slot per day
 * - Regenerate, Shuffle, Export to .txt file
 * - Clean tabular output with subject distribution bar chart
 * ============================================================
 */
public class TimetableGenerator {

    // --- Shared Scanner (one instance for the whole program) ------
    static final Scanner sc = new Scanner(System.in);

    // --- Global configuration (filled in getUserInput) ------------
    static int numSubjects; // how many subjects the user enters
    static int numDays; // working days per week
    static int numSlots; // time-slots per day
    static int breakSlot; // 1-based slot index for break (0 = none)

    static String[] subjectNames; // e.g. {"Math","Physics",...}
    static int[] priorities; // 1=Low, 2=Normal, 3=High per subject

    // --- Day names (sliced from the full week array) --------------
    static final String[] ALL_DAYS = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };
    static String[] dayNames; // subset chosen by numDays

    // --- The timetable grid: timetable[day][slot] -----------------
    static String[][] timetable;

    // --- Fixed slots: key = "dayIndex,slotIndex", value = subject -
    static Map<String, String> fixedSlots = new LinkedHashMap<>();

    // -------------------------------------------------------------
    // ENTRY POINT
    // -------------------------------------------------------------
    public static void main(String[] args) {
        clearScreen();
        printBanner();
        getUserInput();
        generateTimetable();
        printTimetable();
        postGenerationMenu();
    }

    // =============================================================
    // SECTION 1 - INPUT COLLECTION
    // =============================================================

    /**
     * Master input method - calls sub-steps in sequence.
     * Validates every value before proceeding.
     */
    static void getUserInput() {
        System.out.println(line('=', 56));
        System.out.println("  STEP 1 OF 4  |  Subject Configuration");
        System.out.println(line('=', 56));
        System.out.println();

        // -- Number of subjects ----------------------------------
        numSubjects = readInt("  How many subjects? (2-15): ", 2, 15);
        subjectNames = new String[numSubjects];
        priorities = new int[numSubjects];

        System.out.println();
        System.out.println("  Priority guide:  1 = Low  |  2 = Normal  |  3 = High");
        System.out.println("  High-priority subjects will fill more slots per week.");
        System.out.println();

        for (int i = 0; i < numSubjects; i++) {
            System.out.println("  Subject " + (i + 1) + " ---------------------------------------");
            System.out.print("    Name     : ");
            String name = sc.nextLine().trim();
            subjectNames[i] = name.isEmpty() ? ("Subject" + (i + 1)) : name;
            priorities[i] = readInt("    Priority : ", 1, 3);
            System.out.println();
        }

        // -- Schedule structure ----------------------------------
        System.out.println(line('=', 56));
        System.out.println("  STEP 2 OF 4  |  Schedule Structure");
        System.out.println(line('=', 56));
        System.out.println();

        numDays = readInt("  Working days per week (1-7): ", 1, 7);
        numSlots = readInt("  Time slots per day   (2-10): ", 2, 10);

        // Build dayNames array
        dayNames = Arrays.copyOfRange(ALL_DAYS, 0, numDays);

        int totalSlots = numDays * numSlots;
        System.out.println();
        System.out.println("  Grid: " + numDays + " days  x  " + numSlots +
                " slots = " + totalSlots + " total slots.");

        // -- Optional: break slot --------------------------------
        System.out.println();
        System.out.println(line('=', 56));
        System.out.println("  STEP 3 OF 4  |  Break / Lunch Slot (Optional)");
        System.out.println(line('=', 56));
        System.out.println();

        System.out.print("  Include a break/lunch slot? (y/n): ");
        String hasBreak = sc.nextLine().trim().toLowerCase();
        if (hasBreak.equals("y") || hasBreak.equals("yes")) {
            breakSlot = readInt(
                    "  Which slot position is the break? (1-" + numSlots + "): ", 1, numSlots);
        } else {
            breakSlot = 0;
        }

        // -- Optional: fixed slots -------------------------------
        System.out.println();
        System.out.println(line('=', 56));
        System.out.println("  STEP 4 OF 4  |  Fixed Slots (Optional)");
        System.out.println(line('=', 56));
        System.out.println();
        System.out.println("  Fixed slots lock a specific subject into a specific");
        System.out.println("  day+slot (e.g., Lab always Thursday slot 3).");
        System.out.println();

        System.out.print("  Add fixed slots? (y/n): ");
        String hasFixed = sc.nextLine().trim().toLowerCase();
        if (hasFixed.equals("y") || hasFixed.equals("yes")) {
            collectFixedSlots();
        }

        System.out.println();
        System.out.println("  All inputs received. Generating your timetable...");
        pause(500);
        System.out.println();
    }

    /**
     * Interactive loop to let the user pin subjects to specific slots.
     */
    static void collectFixedSlots() {
        System.out.println();
        System.out.println("  Type the day number, then the slot, then the subject.");
        System.out.println("  Enter 0 when done.");
        System.out.println();

        while (true) {
            // Show day list
            System.out.println("  Days:");
            for (int i = 0; i < numDays; i++)
                System.out.println("    " + (i + 1) + ". " + dayNames[i]);
            System.out.print("  Day number (0 to finish): ");

            String raw = sc.nextLine().trim();
            if (raw.equals("0") || raw.equalsIgnoreCase("done"))
                break;

            int dayIdx;
            try {
                dayIdx = Integer.parseInt(raw) - 1;
            } catch (NumberFormatException e) {
                System.out.println("  Invalid.");
                continue;
            }

            if (dayIdx < 0 || dayIdx >= numDays) {
                System.out.println("  Day out of range.");
                continue;
            }

            // Slot number
            int slotIdx = readInt(
                    "  Slot number (1-" + numSlots + "): ", 1, numSlots) - 1;

            // Cannot fix the break slot
            if (breakSlot > 0 && slotIdx == breakSlot - 1) {
                System.out.println("  That slot is the break - can't fix a subject there.");
                continue;
            }

            // Check not already fixed
            String key = dayIdx + "," + slotIdx;
            if (fixedSlots.containsKey(key)) {
                System.out.println("  That slot is already fixed to: " + fixedSlots.get(key));
                System.out.print("  Override it? (y/n): ");
                if (!sc.nextLine().trim().equalsIgnoreCase("y"))
                    continue;
            }

            // Subject choice
            System.out.println("  Subjects:");
            for (int i = 0; i < numSubjects; i++)
                System.out.println("    " + (i + 1) + ". " + subjectNames[i]);
            int subjIdx = readInt("  Subject number: ", 1, numSubjects) - 1;

            fixedSlots.put(key, subjectNames[subjIdx]);
            System.out.println("  Locked: " + dayNames[dayIdx] +
                    "  Slot " + (slotIdx + 1) +
                    "  ->  " + subjectNames[subjIdx]);
            System.out.println();
        }
    }

    // =============================================================
    // SECTION 2 - TIMETABLE GENERATION
    // Algorithm: BACKTRACKING (Constraint Satisfaction Problem)
    //
    // Step-by-step:
    // 1. computeAllocation() decides HOW MANY times each subject
    // should appear, proportional to its priority weight.
    // 2. prefillSpecialCells() locks BREAK and FIXED slots.
    // 3. solve() fills every remaining free cell recursively:
    //
    // solve(slotIndex, remaining[]):
    // if slotIndex == totalSlots: return TRUE // done!
    // if cell is pre-filled: return solve(next slot)
    // for each candidate subject (in MRV order):
    // CHECK constraint 1: remaining[subject] > 0
    // CHECK constraint 2: subject != previous in same day
    // CHECK constraint 3: total remaining >= free slots left
    // ASSIGN: timetable[day][slot] = subject
    // remaining[subject]--
    // RECURSE: if solve(next slot) == TRUE, return TRUE
    // BACKTRACK: timetable[day][slot] = null
    // remaining[subject]++
    // return FALSE // no valid candidate found here
    //
    // 4. If the strict solve fails (consecutive constraint too
    // tight for the given input), we retry with that rule
    // relaxed so a solution is still returned.
    // =============================================================

    // -- Backtracking statistics shown after generation ------------
    static int btCalls; // total recursive calls made
    static int btBacktracks; // times an assignment was undone
    static Random btRng = new Random(); // shared RNG for shuffling

    /**
     * Orchestrates the three-phase generation.
     * Phase 1 : Pre-fill BREAK + FIXED cells (locked forever).
     * Phase 2 : Compute per-subject target counts (allocation).
     * Phase 3 : Run the backtracking solver to fill free cells.
     */
    static void generateTimetable() {
        timetable = new String[numDays][numSlots];

        // Phase 1 - lock special cells
        prefillSpecialCells();

        // Phase 2 - decide target allocation per subject
        int freeSlots = countFreeSlots();
        validateFeasibility(freeSlots);
        if (freeSlots == 0)
            return;

        int[] targetAlloc = computeAllocation(freeSlots);

        // Reset statistics
        btCalls = 0;
        btBacktracks = 0;

        // Phase 3 - backtracking solve
        // Attempt A: strict (no consecutive duplicate in the same day)
        System.out.print("  Backtracking solver running");
        int[] remaining = Arrays.copyOf(targetAlloc, numSubjects);
        long deadline = System.currentTimeMillis() + 10_000; // 10 s timeout
        boolean solved = solve(0, remaining, freeSlots, false, deadline);

        // Attempt B: relaxed (allow consecutive if unavoidable)
        if (!solved) {
            System.out.println();
            System.out.println("  [Attempt 1 exhausted] Retrying with relaxed"
                    + " consecutive constraint...");
            System.out.print("  Backtracking solver running");
            resetFreeCells();
            remaining = Arrays.copyOf(targetAlloc, numSubjects);
            deadline = System.currentTimeMillis() + 10_000;
            solved = solve(0, remaining, freeSlots, true, deadline);
        }

        System.out.println();
        if (solved) {
            System.out.printf("  Solved!  Recursive calls: %,d   Backtracks: %,d%n",
                    btCalls, btBacktracks);
        } else {
            System.out.println("  [!] No valid timetable found. "
                    + "Try adding more subjects or fewer slots.");
        }
        System.out.println();
    }

    // =========================================================
    // THE BACKTRACKING FUNCTION
    // =========================================================
    /**
     * Core recursive backtracking solver (CSP approach).
     *
     * Processes slots in linear order 0 .. numDays*numSlots-1,
     * converting each index to (day, slot) on the fly.
     *
     * @param slotIndex   Current linear slot index being filled.
     * @param remaining   Quota left for each subject (mutated in place,
     *                    restored on backtrack).
     * @param freeLeft    Free cells still to be filled from slotIndex
     *                    onwards (used for forward-checking).
     * @param relaxConsec If true, the no-consecutive rule is waived.
     * @param deadline    Wall-clock ms deadline (timeout guard).
     * @return true if a valid complete assignment was found from
     *         this state onward; false otherwise.
     */
    static boolean solve(int slotIndex, int[] remaining, int freeLeft,
            boolean relaxConsec, long deadline) {

        // -- Timeout guard -----------------------------------------
        if (System.currentTimeMillis() > deadline)
            return false;

        btCalls++;
        if (btCalls % 8000 == 0)
            System.out.print("."); // progress dots

        // -- Base case: all slots processed ------------------------
        if (slotIndex == numDays * numSlots)
            return true;

        int day = slotIndex / numSlots;
        int slot = slotIndex % numSlots;

        // -- Skip pre-filled cells (BREAK or FIXED) ----------------
        if (timetable[day][slot] != null) {
            // freeLeft does NOT decrease because this slot was not free
            return solve(slotIndex + 1, remaining, freeLeft,
                    relaxConsec, deadline);
        }

        // -- CONSTRAINT 3 (forward check): total remaining quota
        // must be exactly equal to the number of free slots left.
        // If less, we can never fill the grid. If more, we'd
        // violate quotas. This prunes dead branches early.
        int remTotal = 0;
        for (int r : remaining)
            remTotal += r;
        if (remTotal != freeLeft)
            return false;

        // -- Previous subject in the same day (for constraint 2) ---
        String prevSubject = prevNonBreakSlot(day, slot);

        // -- Try each candidate in MRV (most-remaining-first) order
        Integer[] candidates = candidateOrder(remaining);

        for (int idx : candidates) {

            // CONSTRAINT 1: subject still has remaining quota
            if (remaining[idx] <= 0)
                continue;

            // CONSTRAINT 2: no consecutive same subject in the same day
            if (!relaxConsec && subjectNames[idx].equals(prevSubject))
                continue;

            // -- ASSIGN --------------------------------------------
            timetable[day][slot] = subjectNames[idx];
            remaining[idx]--;

            // -- RECURSE into the next slot ------------------------
            if (solve(slotIndex + 1, remaining, freeLeft - 1,
                    relaxConsec, deadline)) {
                return true; // solution found - propagate success up
            }

            // -- BACKTRACK: undo this assignment and try next ------
            timetable[day][slot] = null;
            remaining[idx]++;
            btBacktracks++;
        }

        // No candidate worked - signal failure to the caller
        return false;
    }

    // =========================================================
    // ALLOCATION CALCULATOR
    // =========================================================
    /**
     * Decides how many times each subject should appear in the grid.
     *
     * Formula (Priority-Weighted Distribution):
     * alloc[i] = floor( priority[i] / totalPriority * freeSlots )
     *
     * Leftover slots (from flooring) go to highest-priority subjects.
     * Every subject gets at least 1 appearance if freeSlots allows.
     *
     * These counts become the hard per-subject quota that backtracking
     * must satisfy exactly.
     */
    static int[] computeAllocation(int freeSlots) {
        int totalWeight = 0;
        for (int p : priorities)
            totalWeight += p;

        int[] alloc = new int[numSubjects];
        int assigned = 0;

        // Proportional floor
        for (int i = 0; i < numSubjects; i++) {
            alloc[i] = (int) Math.floor(
                    (double) priorities[i] / totalWeight * freeSlots);
            assigned += alloc[i];
        }

        // Give leftover to highest-priority subjects
        Integer[] order = sortedByPriorityDesc();
        int leftover = freeSlots - assigned;
        for (int i = 0; i < leftover; i++)
            alloc[order[i % numSubjects]]++;

        // Guarantee every subject appears at least once if possible
        for (int i = 0; i < numSubjects; i++) {
            if (alloc[i] == 0 && freeSlots >= numSubjects) {
                int maxIdx = indexOfMax(alloc);
                if (alloc[maxIdx] > 1) {
                    alloc[maxIdx]--;
                    alloc[i] = 1;
                }
            }
        }

        return alloc;
    }

    // =========================================================
    // CANDIDATE ORDERING (MRV heuristic)
    // =========================================================
    /**
     * Returns subject indices ordered by:
     * Primary : remaining count DESCENDING
     * (subjects with most quota left are tried first,
     * which mirrors priority and prevents quota-overflow
     * dead-ends deeper in the tree)
     * Secondary : random shuffle within equal-remaining groups
     * (so every call to generateTimetable gives a
     * different valid layout)
     *
     * MRV (Most Remaining Values) is a standard CSP heuristic that
     * can reduce the effective branching factor dramatically.
     */
    static Integer[] candidateOrder(int[] remaining) {
        Integer[] idx = new Integer[numSubjects];
        for (int i = 0; i < numSubjects; i++)
            idx[i] = i;

        // Fisher-Yates shuffle for within-group randomness
        for (int i = numSubjects - 1; i > 0; i--) {
            int j = btRng.nextInt(i + 1);
            Integer tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }

        // Stable descending sort by remaining count
        Arrays.sort(idx, (a, b) -> Integer.compare(remaining[b], remaining[a]));
        return idx;
    }

    // =========================================================
    // SUPPORTING HELPERS FOR THE SOLVER
    // =========================================================

    /**
     * Pre-fill BREAK cells and user-defined FIXED cells.
     * Everything else stays null until solve() fills it.
     */
    static void prefillSpecialCells() {
        for (int d = 0; d < numDays; d++) {
            for (int s = 0; s < numSlots; s++) {
                if (breakSlot > 0 && s == breakSlot - 1) {
                    timetable[d][s] = "BREAK";
                    continue;
                }
                String key = d + "," + s;
                if (fixedSlots.containsKey(key))
                    timetable[d][s] = fixedSlots.get(key);
            }
        }
    }

    /** Counts null (free) cells in the whole grid. */
    static int countFreeSlots() {
        int n = 0;
        for (String[] row : timetable)
            for (String cell : row)
                if (cell == null)
                    n++;
        return n;
    }

    /** Resets all non-BREAK, non-FIXED cells back to null for a retry. */
    static void resetFreeCells() {
        for (int d = 0; d < numDays; d++)
            for (int s = 0; s < numSlots; s++) {
                String key = d + "," + s;
                boolean isBreak = timetable[d][s] != null
                        && timetable[d][s].equals("BREAK");
                boolean isFixed = fixedSlots.containsKey(key);
                if (!isBreak && !isFixed)
                    timetable[d][s] = null;
            }
    }

    /** Warn the user early if the input looks infeasible. */
    static void validateFeasibility(int freeSlots) {
        if (freeSlots == 0) {
            System.out.println("  [!] All slots are break or fixed - nothing to generate.");
            return;
        }
        if (freeSlots < numSubjects) {
            System.out.println("  [!] Warning: only " + freeSlots
                    + " free slots for " + numSubjects + " subjects.");
            System.out.println("      Some subjects may not appear.");
        }
    }

    /**
     * Scans backward in the same day (skipping BREAK) to return
     * the subject most recently placed before this slot.
     * Returns null if this is the first content slot of the day.
     */
    static String prevNonBreakSlot(int day, int slot) {
        for (int s = slot - 1; s >= 0; s--) {
            if (timetable[day][s] != null && !timetable[day][s].equals("BREAK"))
                return timetable[day][s];
        }
        return null;
    }

    // =============================================================
    // SECTION 3 - OUTPUT / PRINTING
    // =============================================================

    /**
     * Renders the timetable as an ASCII table in the terminal.
     * Column width adapts to the longest subject name.
     */
    static void printTimetable() {
        System.out.println();
        System.out.println("  " + line('=', 54));
        System.out.println("           GENERATED TIMETABLE");
        System.out.println("  " + line('=', 54));
        System.out.println();

        // -- Adaptive column widths ------------------------------
        int colW = 9; // minimum content width per slot column
        for (String s : subjectNames)
            if (s.length() + 2 > colW)
                colW = s.length() + 2;
        colW = Math.min(colW, 13); // cap so table doesn't overflow

        int dayW = 11; // width of the "Day" column

        // -- Header row -----------------------------------------
        System.out.print("  " + padRight(" Day", dayW) + " |");
        for (int s = 0; s < numSlots; s++) {
            String h = "Slot " + (s + 1);
            System.out.print(padCenter(h, colW) + "|");
        }
        System.out.println();

        // -- Header / body separator -----------------------------
        printRowSeparator(dayW, colW);

        // -- Data rows ------------------------------------------
        for (int d = 0; d < numDays; d++) {
            System.out.print("  " + padRight(" " + dayNames[d], dayW) + " |");
            for (int s = 0; s < numSlots; s++) {
                String cell = timetable[d][s];
                if (cell == null)
                    cell = "---";
                // Truncate very long names to fit column
                if (cell.length() > colW - 2)
                    cell = cell.substring(0, colW - 3) + ".";
                System.out.print(padCenter(cell, colW) + "|");
            }
            System.out.println();
            printRowSeparator(dayW, colW);
        }

        System.out.println();
        printDistributionChart();
    }

    /** Horizontal divider line for the table. */
    static void printRowSeparator(int dayW, int colW) {
        System.out.print("  " + line('-', dayW + 1) + "+");
        for (int s = 0; s < numSlots; s++)
            System.out.print(line('-', colW) + "+");
        System.out.println();
    }

    /**
     * Prints a bar-chart style summary showing how often each
     * subject appears, and its priority.
     */
    static void printDistributionChart() {
        System.out.println("  " + line('-', 54));
        System.out.println("  SUBJECT DISTRIBUTION");
        System.out.println("  " + line('-', 54));

        // Count occurrences
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String s : subjectNames)
            freq.put(s, 0);
        for (int d = 0; d < numDays; d++)
            for (int s = 0; s < numSlots; s++) {
                String cell = timetable[d][s];
                if (cell != null && !cell.equals("BREAK") && freq.containsKey(cell))
                    freq.merge(cell, 1, Integer::sum);
            }

        int maxFreq = Collections.max(freq.values());

        for (int i = 0; i < numSubjects; i++) {
            String name = subjectNames[i];
            int count = freq.getOrDefault(name, 0);
            String prioLbl = priorities[i] == 3 ? "[HIGH  ]"
                    : priorities[i] == 2 ? "[NORMAL]" : "[LOW   ]";
            int barLen = (maxFreq > 0) ? (int) Math.round((double) count / maxFreq * 20) : 0;

            System.out.printf("  %-14s %s  |%s  %d lecture%s%n",
                    name, prioLbl,
                    "#".repeat(barLen) + " ".repeat(20 - barLen),
                    count, count != 1 ? "s" : "");
        }
        System.out.println();
    }

    // =============================================================
    // SECTION 4 - POST-GENERATION MENU
    // =============================================================

    /**
     * Interactive menu shown after the first timetable is displayed.
     * Loops until the user chooses Exit.
     */
    static void postGenerationMenu() {
        while (true) {
            System.out.println("  " + line('=', 54));
            System.out.println("  WHAT WOULD YOU LIKE TO DO NEXT?");
            System.out.println("  " + line('-', 54));
            System.out.println("  [1]  Regenerate  (same config, new random layout)");
            System.out.println("  [2]  Shuffle     (jumble existing assignments)");
            System.out.println("  [3]  Export      (save to a .txt file)");
            System.out.println("  [4]  Change inputs & regenerate from scratch");
            System.out.println("  [5]  Print timetable again");
            System.out.println("  [6]  Exit");
            System.out.println("  " + line('=', 54));
            System.out.print("  Your choice: ");

            String choice = sc.nextLine().trim();
            System.out.println();

            switch (choice) {
                case "1":
                    // Keep subjects / config, wipe fixedSlots (user can re-enter)
                    System.out.print("  Keep your fixed slots? (y/n): ");
                    if (sc.nextLine().trim().equalsIgnoreCase("n"))
                        fixedSlots.clear();
                    generateTimetable();
                    printTimetable();
                    break;
                case "2":
                    shuffleTimetable();
                    printTimetable();
                    break;
                case "3":
                    exportToFile();
                    break;
                case "4":
                    fixedSlots.clear();
                    getUserInput();
                    generateTimetable();
                    printTimetable();
                    break;
                case "5":
                    printTimetable();
                    break;
                case "6":
                    System.out.println("  Goodbye! Good luck this semester.");
                    System.out.println();
                    sc.close();
                    return;
                default:
                    System.out.println("  Invalid option - please enter 1 to 6.\n");
            }
        }
    }

    // =============================================================
    // SECTION 5 - SHUFFLE
    // =============================================================

    /**
     * Collects all non-BREAK, non-fixed cells into a list,
     * shuffles the list, then writes them back in the same positions.
     * Break cells and fixed cells remain locked in place.
     */
    static void shuffleTimetable() {
        // Collect moveable cell contents
        List<String> moveable = new ArrayList<>();
        for (int d = 0; d < numDays; d++)
            for (int s = 0; s < numSlots; s++) {
                String cell = timetable[d][s];
                String key = d + "," + s;
                if (cell != null && !cell.equals("BREAK") && !fixedSlots.containsKey(key))
                    moveable.add(cell);
            }

        // Randomise
        Collections.shuffle(moveable, new Random());

        // Write back
        int idx = 0;
        for (int d = 0; d < numDays; d++)
            for (int s = 0; s < numSlots; s++) {
                String key = d + "," + s;
                if (timetable[d][s] != null
                        && !timetable[d][s].equals("BREAK")
                        && !fixedSlots.containsKey(key))
                    timetable[d][s] = moveable.get(idx++);
            }

        System.out.println("  Timetable shuffled successfully.\n");
    }

    // =============================================================
    // SECTION 6 - EXPORT TO TEXT FILE
    // =============================================================

    /**
     * Writes the timetable (same format as the terminal output) to
     * a .txt file in the current working directory.
     * The filename includes a timestamp to avoid overwriting.
     */
    static void exportToFile() {
        String filename = "timetable_" + System.currentTimeMillis() + ".txt";

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {

            pw.println("AUTOMATIC TIMETABLE GENERATOR");
            pw.println("Generated : " + new Date());
            pw.println("=".repeat(60));
            pw.println();

            // -- Column widths (same logic as printTimetable) -----
            int colW = 9;
            for (String s : subjectNames)
                if (s.length() + 2 > colW)
                    colW = s.length() + 2;
            colW = Math.min(colW, 13);
            int dayW = 11;

            // -- Header -------------------------------------------
            pw.print(padRight(" Day", dayW) + " |");
            for (int s = 0; s < numSlots; s++)
                pw.print(padCenter("Slot " + (s + 1), colW) + "|");
            pw.println();
            pw.print(line('-', dayW + 1) + "+");
            for (int s = 0; s < numSlots; s++)
                pw.print(line('-', colW) + "+");
            pw.println();

            // -- Data rows ----------------------------------------
            for (int d = 0; d < numDays; d++) {
                pw.print(padRight(" " + dayNames[d], dayW) + " |");
                for (int s = 0; s < numSlots; s++) {
                    String cell = timetable[d][s] != null ? timetable[d][s] : "---";
                    if (cell.length() > colW - 2)
                        cell = cell.substring(0, colW - 3) + ".";
                    pw.print(padCenter(cell, colW) + "|");
                }
                pw.println();
                pw.print(line('-', dayW + 1) + "+");
                for (int s = 0; s < numSlots; s++)
                    pw.print(line('-', colW) + "+");
                pw.println();
            }

            pw.println();
            pw.println("SUBJECT DISTRIBUTION");
            pw.println("-".repeat(40));

            // Count occurrences
            Map<String, Integer> freq = new LinkedHashMap<>();
            for (String s : subjectNames)
                freq.put(s, 0);
            for (int d = 0; d < numDays; d++)
                for (int s = 0; s < numSlots; s++) {
                    String cell = timetable[d][s];
                    if (cell != null && !cell.equals("BREAK") && freq.containsKey(cell))
                        freq.merge(cell, 1, Integer::sum);
                }

            for (int i = 0; i < numSubjects; i++) {
                int cnt = freq.getOrDefault(subjectNames[i], 0);
                String prio = priorities[i] == 3 ? "High"
                        : priorities[i] == 2 ? "Normal" : "Low";
                pw.printf("%-16s : %2d lecture%-1s  [Priority: %s]%n",
                        subjectNames[i], cnt, cnt != 1 ? "s" : "", prio);
            }

            pw.println();
            pw.println("Exported by: Automatic Timetable Generator (Java CLI)");

        } catch (IOException e) {
            System.out.println("  Export failed: " + e.getMessage());
            return;
        }

        System.out.println("  Saved to : " + filename);
        try {
            System.out.println("  Location : " + new File(filename).getCanonicalPath());
        } catch (IOException ignored) {
        }
        System.out.println();
    }

    // =============================================================
    // SECTION 7 - UTILITY HELPERS
    // =============================================================

    /**
     * Reads an integer from stdin, repeating until the value
     * is within [min, max].
     */
    static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v >= min && v <= max)
                    return v;
                System.out.println("  Please enter a number between " + min +
                        " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  That doesn't look like a number. Try again.");
            }
        }
    }

    /**
     * Returns an array of subject indices sorted by priority descending.
     * Used to decide which subject gets leftover slots.
     */
    static Integer[] sortedByPriorityDesc() {
        Integer[] idx = new Integer[numSubjects];
        for (int i = 0; i < numSubjects; i++)
            idx[i] = i;
        Arrays.sort(idx, (a, b) -> priorities[b] - priorities[a]);
        return idx;
    }

    /** Returns the index of the maximum value in an int array. */
    static int indexOfMax(int[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++)
            if (arr[i] > arr[best])
                best = i;
        return best;
    }

    /**
     * Left-pad a string to exactly 'width' characters.
     * Truncates if longer.
     */
    static String padRight(String s, int width) {
        if (s.length() >= width)
            return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    /**
     * Center a string within exactly 'width' characters.
     * Truncates if longer.
     */
    static String padCenter(String s, int width) {
        if (s.length() >= width)
            return s.substring(0, width);
        int left = (width - s.length()) / 2;
        int right = width - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }

    /** Builds a string of 'count' repetitions of char 'ch'. */
    static String line(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

    /** Clears the terminal screen (best-effort; works on most terminals). */
    static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /** Pauses execution for the given number of milliseconds. */
    static void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /** Prints the startup banner. */
    static void printBanner() {
        System.out.println("  " + line('=', 54));
        System.out.println("       AUTOMATIC TIMETABLE GENERATOR");
        System.out.println("       Engineering Semester Scheduler  |  Java CLI");
        System.out.println("  " + line('=', 54));
        System.out.println("  Algorithm : Backtracking (CSP) + Priority Weighting");
        System.out.println("  Features  : Priorities, Fixed Slots, Break Slots,");
        System.out.println("              Shuffle, Export to file");
        System.out.println("  " + line('=', 54));
        System.out.println();
    }
}
