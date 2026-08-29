import importlib.util
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("audit-parameter-modes.py")
SPEC = importlib.util.spec_from_file_location("audit_parameter_modes", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class AuditParserTest(unittest.TestCase):
    def test_extended_choice_signals_are_aggregated_without_values(self):
        node = ET.fromstring(
            """
            <com.cwctravel.hudson.plugins.extended__choice__parameter.ExtendedChoiceParameterDefinition>
              <type>PT_MULTI_SELECT</type>
              <value>android,,ios</value>
              <defaultValue>Choose platforms</defaultValue>
              <multiSelectDelimiter>,</multiSelectDelimiter>
              <quoteValue>false</quoteValue>
            </com.cwctravel.hudson.plugins.extended__choice__parameter.ExtendedChoiceParameterDefinition>
            """
        )

        family, mode, detail = MODULE.classify_definition(node)

        self.assertEqual("extended-choice", family)
        self.assertEqual("PT_MULTI_SELECT", mode)
        self.assertFalse(detail["dynamic"])
        self.assertTrue(detail["stale_default"])
        self.assertTrue(detail["empty_option"])

    def test_core_file_and_stashed_file_are_transport_families(self):
        file_node = ET.fromstring("<hudson.model.FileParameterDefinition />")
        stashed_node = ET.fromstring(
            "<io.jenkins.plugins.file__parameters.StashedFileParameterDefinition />"
        )

        self.assertEqual("file", MODULE.classify_definition(file_node)[0])
        self.assertEqual("file", MODULE.classify_definition(stashed_node)[0])

    def test_active_choices_cascade_reports_dependency_presence_only(self):
        node = ET.fromstring(
            """
            <org.biouno.unochoice.CascadeChoiceParameter>
              <referencedParameters>ENVIRONMENT,REGION</referencedParameters>
            </org.biouno.unochoice.CascadeChoiceParameter>
            """
        )

        family, mode, detail = MODULE.classify_definition(node)

        self.assertEqual("active-choices", family)
        self.assertEqual("CascadeChoiceParameter", mode)
        self.assertTrue(detail["dependency_declared"])


if __name__ == "__main__":
    unittest.main()
