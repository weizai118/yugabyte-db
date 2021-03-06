/*--------------------------------------------------------------------------------------------------
 *
 * ybctype.c
 *        Commands for creating and altering table structures and settings
 *
 * Copyright (c) YugaByte, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 * http: *www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * IDENTIFICATION
 *        src/backend/catalog/ybctype.c
 *
 *--------------------------------------------------------------------------------------------------
 */

#include "postgres.h"

#include "catalog/pg_type.h"
#include "commands/ybctype.h"
#include "parser/parse_type.h"

#include "yb/yql/pggate/ybc_pggate.h"

/*
 * TODO For now we use the CQL/YQL types here, eventually we should use the
 * internal (protobuf) types.
 */
YBCPgDataType
YBCDataTypeFromName(TypeName *typeName)
{
	Oid			type_id;
	int32		typmod;

	typenameTypeIdAndMod(NULL /* parseState */ , typeName, &type_id, &typmod);

	if (typmod != -1)
	{
		ereport(ERROR,
				(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
				 errmsg("Type modifiers are not supported yet: %d", typmod)));
	}

	switch (type_id)
	{
		case BOOLOID:
		case BYTEAOID:
		case CHAROID:
		case NAMEOID:
			ereport(ERROR,
							(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
							 errmsg("Datatype not yet supported: %d", type_id)));
			break;
		case INT8OID:
			return 4;
		case INT2OID:
			return 2;
		case INT2VECTOROID:
			ereport(ERROR,
							(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
							 errmsg("Datatype not yet supported: %d", type_id)));
			break;
		case INT4OID:
			return 3;
		case REGPROCOID:
			ereport(ERROR,
							(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
							 errmsg("Datatype not yet supported: %d", type_id)));
			break;
		case TEXTOID:
			return 5;
		case OIDOID:
		case TIDOID:
		case XIDOID:
		case CIDOID:
		case OIDVECTOROID:
		case POINTOID:
		case LSEGOID:
		case PATHOID:
		case BOXOID:
		case POLYGONOID:
		case LINEOID:
			ereport(ERROR,
							(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
							 errmsg("Datatype not yet supported: %d", type_id)));
			break;
		case FLOAT4OID:
			return 7;
		case FLOAT8OID:
			return 8;
		case ABSTIMEOID:
		case RELTIMEOID:
		case TINTERVALOID:
		case UNKNOWNOID:
		case CIRCLEOID:
		case CASHOID:
		case INETOID:
		case CIDROID:
		case BPCHAROID:
			ereport(ERROR,
							(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
							 errmsg("Datatype not yet supported: %d", type_id)));
			break;
		case VARCHAROID:
			return 5;
		case DATEOID:
		case TIMEOID:
		case TIMESTAMPOID:
		case TIMESTAMPTZOID:
		case INTERVALOID:
		case TIMETZOID:
		case VARBITOID:
		case NUMERICOID:
		case REFCURSOROID:
		case REGPROCEDUREOID:
		case REGOPEROID:
		case REGOPERATOROID:
		case REGCLASSOID:
		case REGTYPEOID:
		case REGROLEOID:
		case REGNAMESPACEOID:
		case REGTYPEARRAYOID:
		case UUIDOID:
		case LSNOID:
		case TSVECTOROID:
		case GTSVECTOROID:
		case TSQUERYOID:
		case REGCONFIGOID:
		case REGDICTIONARYOID:
		case JSONBOID:
		case INT4RANGEOID:
		default:
			ereport(ERROR,
							(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
							 errmsg("Datatype not yet supported: %d", type_id)));
			break;
	}
	return -1;
}
