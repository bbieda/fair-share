<?php
// Throwaway local test server only (docker network, not reachable from outside). Never use these values on AGH.
return [
    'db_host' => 'fsdb',
    'db_port' => 3306,
    'db_name' => 'fairshare',
    'db_user' => 'fs',
    'db_pass' => 'fs-test-pass',
];
