--
--   Copyright 2014 Nortal AS
--
--   Licensed under the Apache License, Version 2.0 (the "License");
--   you may not use this file except in compliance with the License.
--   You may obtain a copy of the License at
--
--       http://www.apache.org/licenses/LICENSE-2.0
--
--   Unless required by applicable law or agreed to in writing, software
--   distributed under the License is distributed on an "AS IS" BASIS,
--   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--   See the License for the specific language governing permissions and
--   limitations under the License.
--

CREATE TABLE <<TABLE_NAME>> (MODULE_NAME VARCHAR(30), DB_CHANGE_CODE VARCHAR(200), SQL_TEXT VARCHAR(2000), ROWS_UPDATED NUMERIC(18), ERROR_CODE NUMERIC(18), ERROR_TEXT VARCHAR(1000), UPDATE_TIME TIMESTAMP(6), EXECUTION_TIME NUMERIC(10,2))