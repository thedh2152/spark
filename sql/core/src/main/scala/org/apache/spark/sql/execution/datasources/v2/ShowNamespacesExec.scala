/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.v2

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalog.v2.CatalogV2Implicits.NamespaceHelper
import org.apache.spark.sql.catalog.v2.SupportsNamespaces
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.catalyst.expressions.{Attribute, GenericRowWithSchema}
import org.apache.spark.sql.catalyst.util.StringUtils
import org.apache.spark.sql.execution.LeafExecNode

/**
 * Physical plan node for showing namespaces.
 */
case class ShowNamespacesExec(
    output: Seq[Attribute],
    catalog: SupportsNamespaces,
    namespace: Option[Seq[String]],
    pattern: Option[String])
    extends LeafExecNode {
  override protected def doExecute(): RDD[InternalRow] = {
    val namespaces = namespace.map { ns =>
        if (ns.nonEmpty) {
          catalog.listNamespaces(ns.toArray)
        } else {
          catalog.listNamespaces()
        }
      }
      .getOrElse(catalog.listNamespaces())

    val rows = new ArrayBuffer[InternalRow]()
    val encoder = RowEncoder(schema).resolveAndBind()

    namespaces.map(_.quoted).map { ns =>
      if (pattern.map(StringUtils.filterPattern(Seq(ns), _).nonEmpty).getOrElse(true)) {
        rows += encoder
          .toRow(new GenericRowWithSchema(Array(ns), schema))
          .copy()
      }
    }

    sparkContext.parallelize(rows, 1)
  }
}
