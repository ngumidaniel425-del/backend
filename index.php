<?php
// Force allow cross-origin
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST, GET, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') { exit; }
header('Content-Type: application/json');

// --- DATABASE CONNECTION (PostgreSQL for Neon) ---
// Render will provide this via your Environment Variables
$connection_string = getenv('DATABASE_URL'); 
$conn = pg_connect($connection_string);

if (!$conn) {
    echo json_encode(["status" => "error", "message" => "Connection failed: " . pg_last_error()]);
    exit();
}

$type = isset($_GET['type']) ? $_GET['type'] : '';
$jsonInput = file_get_contents('php://input');
$data = json_decode($jsonInput, true);

// --- 1. SINGLE SCAN UPLOAD ---
if ($type == "single_upload") {
    $query = "INSERT INTO attendance_sync (student_adm, student_name, class_name, lesson_name, attendance_date, sync_time) VALUES ($1, $2, $3, $4, CURRENT_DATE, CURRENT_TIME)";
    $result = pg_query_params($conn, $query, array($data['admission'], $data['fullname'], $data['class_name'], $data['lesson_name']));
    if ($result) echo json_encode(["status" => "success"]);
    else echo json_encode(["status" => "error", "message" => pg_last_error($conn)]);

// --- 3. STUDENT REGISTRATION ---
} elseif ($type == "add_student" || $type == "register_student") {
    $adm = $data['admission'];
    $name = $data['fullname'];
    $class = $data['class_name'];
    $school = $data['school_name'];

    $query = "INSERT INTO students_master (admission, fullname, class_name, school_name) 
              VALUES ($1, $2, $3, $4) 
              ON CONFLICT (admission, school_name) 
              DO UPDATE SET fullname = EXCLUDED.fullname, class_name = EXCLUDED.class_name";
    $result = pg_query_params($conn, $query, array($adm, $name, $class, $school));
    if ($result) echo json_encode(["status" => "success"]);
    else echo json_encode(["status" => "error", "message" => pg_last_error($conn)]);

// --- 4. FETCH ALL ---
} elseif ($type == 'fetch_all' || $type == 'fetch_students') {
    $school = isset($_GET['school']) ? $_GET['school'] : '';
    $query = "SELECT admission, fullname, class_name, school_name FROM students_master WHERE school_name = $1";
    $result = pg_query_params($conn, $query, array($school));
    
    $students = [];
    while($row = pg_fetch_assoc($result)) { 
        $students[] = $row; 
    }
    echo json_encode($students);
}

pg_close($conn);
?>
